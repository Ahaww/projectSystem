package edu.wylie.crs.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.wylie.crs.common.ApiException;
import edu.wylie.crs.domain.ItemStatus;
import edu.wylie.crs.domain.ItemType;
import edu.wylie.crs.domain.Role;
import edu.wylie.crs.domain.ScheduleStatus;
import edu.wylie.crs.dto.RegistrarDtos.CloseResultVO;
import edu.wylie.crs.dto.RegistrarDtos.ProfessorUpsertRequest;
import edu.wylie.crs.dto.RegistrarDtos.ProfessorVO;
import edu.wylie.crs.dto.RegistrarDtos.StudentUpsertRequest;
import edu.wylie.crs.dto.RegistrarDtos.StudentVO;
import edu.wylie.crs.entity.CourseOffering;
import edu.wylie.crs.entity.Enrollment;
import edu.wylie.crs.entity.IdSequence;
import edu.wylie.crs.entity.Professor;
import edu.wylie.crs.entity.ScheduleItemEntity;
import edu.wylie.crs.entity.Student;
import edu.wylie.crs.entity.StudentSchedule;
import edu.wylie.crs.entity.TermConfig;
import edu.wylie.crs.entity.UserAccount;
import edu.wylie.crs.service.BillingRetryService;
import edu.wylie.crs.repository.CourseOfferingRepository;
import edu.wylie.crs.repository.EnrollmentRepository;
import edu.wylie.crs.repository.IdSequenceRepository;
import edu.wylie.crs.repository.ProfessorRepository;
import edu.wylie.crs.repository.ScheduleItemRepository;
import edu.wylie.crs.repository.StudentRepository;
import edu.wylie.crs.repository.StudentScheduleRepository;
import edu.wylie.crs.repository.TeachingAssignmentRepository;
import edu.wylie.crs.repository.TermConfigRepository;
import edu.wylie.crs.repository.UserAccountRepository;
import edu.wylie.crs.service.RegistrarService;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
@Service
public class RegistrarServiceImpl implements RegistrarService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final int MIN_ENROLLMENT = 3;
    private static final int MAX_PRIMARY = 4;
    private static final Set<String> PASSING_GRADES = Set.of("A", "B", "C", "D");

    private final StudentRepository studentRepository;
    private final ProfessorRepository professorRepository;
    private final UserAccountRepository userAccountRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StudentScheduleRepository studentScheduleRepository;
    private final ScheduleItemRepository scheduleItemRepository;
    private final TeachingAssignmentRepository teachingAssignmentRepository;
    private final CourseOfferingRepository courseOfferingRepository;
    private final TermConfigRepository termConfigRepository;
    private final IdSequenceRepository idSequenceRepository;
    private final PasswordEncoder passwordEncoder;
    private final BillingRetryService billingRetryService;
    private final ObjectMapper objectMapper;
    private final String currentTerm;

    public RegistrarServiceImpl(
            StudentRepository studentRepository,
            ProfessorRepository professorRepository,
            UserAccountRepository userAccountRepository,
            EnrollmentRepository enrollmentRepository,
            StudentScheduleRepository studentScheduleRepository,
            ScheduleItemRepository scheduleItemRepository,
            TeachingAssignmentRepository teachingAssignmentRepository,
            CourseOfferingRepository courseOfferingRepository,
            TermConfigRepository termConfigRepository,
            IdSequenceRepository idSequenceRepository,
            PasswordEncoder passwordEncoder,
            BillingRetryService billingRetryService,
            ObjectMapper objectMapper,
            @Value("${crs.term.current}") String currentTerm
    ) {
        this.studentRepository = studentRepository;
        this.professorRepository = professorRepository;
        this.userAccountRepository = userAccountRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.studentScheduleRepository = studentScheduleRepository;
        this.scheduleItemRepository = scheduleItemRepository;
        this.teachingAssignmentRepository = teachingAssignmentRepository;
        this.courseOfferingRepository = courseOfferingRepository;
        this.termConfigRepository = termConfigRepository;
        this.idSequenceRepository = idSequenceRepository;
        this.passwordEncoder = passwordEncoder;
        this.billingRetryService = billingRetryService;
        this.objectMapper = objectMapper;
        this.currentTerm = currentTerm;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentVO> listStudents() {
        return studentRepository.findAll().stream()
                .sorted(Comparator.comparing(Student::getId))
                .map(this::toStudentVo)
                .toList();
    }

    @Override
    @Transactional
    public StudentVO addStudent(StudentUpsertRequest request) {
        String id = allocateId("S", studentRepository.findAll().stream().map(Student::getId).toList());
        Student student = new Student();
        student.setId(id);
        applyStudent(student, request);
        studentRepository.save(student);
        createLoginAccount(id, request.name(), Role.STUDENT);
        return toStudentVo(student);
    }

    @Override
    @Transactional
    public void updateStudent(String id, StudentUpsertRequest request) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "学生不存在"));
        applyStudent(student, request);
        studentRepository.save(student);
        syncAccountName(id, request.name());
    }

    @Override
    @Transactional
    public void deleteStudent(String id) {
        if (!studentRepository.existsById(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "学生不存在");
        }
        if (enrollmentRepository.existsByStudentIdAndTerm(id, currentTerm)) {
            throw new ApiException(HttpStatus.CONFLICT, "该学生本学期仍有在册选课，不能删除");
        }
        for (StudentSchedule schedule : studentScheduleRepository.findByStudentId(id)) {
            scheduleItemRepository.deleteByScheduleId(schedule.getId());
        }
        studentScheduleRepository.deleteByStudentId(id);
        enrollmentRepository.deleteByStudentId(id);
        userAccountRepository.findById(id).ifPresent(userAccountRepository::delete);
        studentRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProfessorVO> listProfessors() {
        return professorRepository.findAll().stream()
                .sorted(Comparator.comparing(Professor::getId))
                .map(this::toProfessorVo)
                .toList();
    }

    @Override
    @Transactional
    public ProfessorVO addProfessor(ProfessorUpsertRequest request) {
        String id = allocateId("P", professorRepository.findAll().stream().map(Professor::getId).toList());
        Professor professor = new Professor();
        professor.setId(id);
        applyProfessor(professor, request);
        professorRepository.save(professor);
        createLoginAccount(id, request.name(), Role.PROFESSOR);
        return toProfessorVo(professor);
    }

    @Override
    @Transactional
    public void updateProfessor(String id, ProfessorUpsertRequest request) {
        Professor professor = professorRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "教师不存在"));
        applyProfessor(professor, request);
        professorRepository.save(professor);
        syncAccountName(id, request.name());
    }

    @Override
    @Transactional
    public void deleteProfessor(String id) {
        if (!professorRepository.existsById(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "教师不存在");
        }
        if (teachingAssignmentRepository.existsByProfessorIdAndTerm(id, currentTerm)
                || courseOfferingRepository.existsByProfessorIdAndTerm(id, currentTerm)) {
            throw new ApiException(HttpStatus.CONFLICT, "该教授本学期仍有任教安排，不能删除");
        }
        teachingAssignmentRepository.deleteByProfessorId(id);
        userAccountRepository.findById(id).ifPresent(userAccountRepository::delete);
        professorRepository.deleteById(id);
    }

    /**
     * 关闭注册批处理（PLAN 3.7）：同一事务内完成领域变更。
     * billed = 最终课表仍有 enrolled 条目、因而发起计费的学生人数。
     */
    @Override
    @Transactional
    public CloseResultVO closeRegistration() {
        TermConfig term;
        try {
            term = termConfigRepository.lockByTerm(currentTerm)
                    .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "当前学期未配置"));
        } catch (PessimisticLockException | LockTimeoutException | PessimisticLockingFailureException e) {
            throw new ApiException(HttpStatus.CONFLICT, "注册进行中，请稍后重试");
        }
        if (term.isRegistrationClosed()) {
            throw new ApiException(HttpStatus.CONFLICT, "注册已经关闭，不能重复执行");
        }

        List<String> cancelled = new ArrayList<>();
        Map<String, CourseOffering> offerings = loadOfferings();

        for (CourseOffering offering : offerings.values()) {
            if (offering.isCancelled()) {
                continue;
            }
            if (!hasProfessor(offering)) {
                cancelOffering(offering, offerings, cancelled);
            }
        }

        reconcileQualifyingOfferings(offerings);

        List<String> leveled = levelSubmittedSchedules(offerings);

        for (CourseOffering offering : offerings.values()) {
            offering.setOfferingClosed(true);
            courseOfferingRepository.save(offering);
        }

        offerings = loadOfferings();
        for (CourseOffering offering : offerings.values()) {
            if (offering.isCancelled()) {
                continue;
            }
            if (seatsTaken(offering) < MIN_ENROLLMENT) {
                cancelOffering(offering, offerings, cancelled);
            }
        }

        List<Map<String, Object>> snapshots = collectBillingSnapshots();
        int billed = billingRetryService.enqueue(snapshots);
        billingRetryService.dispatchAfterCommit();

        term.setRegistrationClosed(true);
        termConfigRepository.save(term);

        return new CloseResultVO(List.copyOf(cancelled), billed, List.copyOf(leveled));
    }

    private Map<String, CourseOffering> loadOfferings() {
        return courseOfferingRepository.findByTerm(currentTerm).stream()
                .collect(Collectors.toMap(CourseOffering::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    private boolean hasProfessor(CourseOffering offering) {
        if (offering.getProfessorId() != null && !offering.getProfessorId().isBlank()) {
            return true;
        }
        return teachingAssignmentRepository.existsByOfferingIdAndTerm(offering.getId(), currentTerm);
    }

    private void cancelOffering(CourseOffering offering, Map<String, CourseOffering> offerings, List<String> cancelled) {
        offering.setCancelled(true);
        offering.setOfferingClosed(true);
        courseOfferingRepository.save(offering);
        cancelled.add(offering.getId());

        for (StudentSchedule schedule : studentScheduleRepository.findByTerm(currentTerm)) {
            List<ScheduleItemEntity> items = scheduleItemRepository.findByScheduleId(schedule.getId());
            for (ScheduleItemEntity item : items) {
                if (!offering.getId().equals(item.getOfferingId())) {
                    continue;
                }
                if (item.getStatus() == ItemStatus.enrolled) {
                    courseOfferingRepository.decrementSeat(offering.getId());
                    enrollmentRepository.deleteByOfferingIdAndStudentId(offering.getId(), schedule.getStudentId());
                }
                scheduleItemRepository.delete(item);
            }
        }
        enrollmentRepository.deleteByOfferingId(offering.getId());
        offerings.put(offering.getId(), courseOfferingRepository.findById(offering.getId()).orElse(offering));
    }

    /** 有教授且 enrolled≥3：保证 Enrollment 与已 enrolled 课表条目一致（不改 seatsTaken 的目录快照）。 */
    private void reconcileQualifyingOfferings(Map<String, CourseOffering> offerings) {
        for (CourseOffering offering : offerings.values()) {
            if (offering.isCancelled() || !hasProfessor(offering) || seatsTaken(offering) < MIN_ENROLLMENT) {
                continue;
            }
            for (StudentSchedule schedule : studentScheduleRepository.findByTerm(currentTerm)) {
                for (ScheduleItemEntity item : scheduleItemRepository.findByScheduleId(schedule.getId())) {
                    if (!offering.getId().equals(item.getOfferingId()) || item.getStatus() != ItemStatus.enrolled) {
                        continue;
                    }
                    if (enrollmentRepository.existsByOfferingIdAndStudentId(offering.getId(), schedule.getStudentId())) {
                        continue;
                    }
                    Enrollment enrollment = new Enrollment();
                    enrollment.setOfferingId(offering.getId());
                    enrollment.setStudentId(schedule.getStudentId());
                    enrollment.setTerm(currentTerm);
                    enrollmentRepository.save(enrollment);
                }
            }
        }
    }

    private List<String> levelSubmittedSchedules(Map<String, CourseOffering> offerings) {
        List<String> leveled = new ArrayList<>();
        List<StudentSchedule> submitted = studentScheduleRepository
                .findByTermAndStatusOrderBySubmitTimeAsc(currentTerm, ScheduleStatus.submitted);
        for (StudentSchedule schedule : submitted) {
            List<ScheduleItemEntity> items = new ArrayList<>(scheduleItemRepository.findByScheduleId(schedule.getId()));
            List<ScheduleItemEntity> enrolledPrimaries = items.stream()
                    .filter(item -> item.getType() == ItemType.primary && item.getStatus() == ItemStatus.enrolled)
                    .collect(Collectors.toCollection(ArrayList::new));
            if (enrolledPrimaries.size() >= MAX_PRIMARY) {
                continue;
            }
            List<ScheduleItemEntity> alternates = items.stream()
                    .filter(item -> item.getType() == ItemType.alternate)
                    .sorted(Comparator.comparing(item -> item.getPriority() == null ? 99 : item.getPriority()))
                    .toList();
            Set<String> completed = completedCourseCodes(schedule.getStudentId());
            for (ScheduleItemEntity alt : alternates) {
                if (enrolledPrimaries.size() >= MAX_PRIMARY) {
                    break;
                }
                CourseOffering offering = offerings.get(alt.getOfferingId());
                if (offering == null || offering.isCancelled() || !hasProfessor(offering)) {
                    continue;
                }
                List<String> prereqs = parseList(offering.getPrerequisitesJson());
                if (prereqs.stream().anyMatch(code -> !completed.contains(code))) {
                    continue;
                }
                boolean conflict = enrolledPrimaries.stream().anyMatch(primary -> {
                    CourseOffering other = offerings.get(primary.getOfferingId());
                    return other != null && overlap(offering, other);
                });
                if (conflict) {
                    continue;
                }
                if (seatsTaken(offering) >= seatsTotal(offering)) {
                    continue;
                }
                int updated = courseOfferingRepository.incrementSeatIfAvailable(offering.getId());
                if (updated == 0) {
                    continue;
                }
                alt.setType(ItemType.primary);
                alt.setStatus(ItemStatus.enrolled);
                alt.setPriority(null);
                scheduleItemRepository.save(alt);
                if (!enrollmentRepository.existsByOfferingIdAndStudentId(offering.getId(), schedule.getStudentId())) {
                    Enrollment enrollment = new Enrollment();
                    enrollment.setOfferingId(offering.getId());
                    enrollment.setStudentId(schedule.getStudentId());
                    enrollment.setTerm(currentTerm);
                    enrollmentRepository.save(enrollment);
                }
                CourseOffering fresh = courseOfferingRepository.findById(offering.getId()).orElse(offering);
                offerings.put(offering.getId(), fresh);
                enrolledPrimaries.add(alt);
                leveled.add(fresh.getCode() == null ? fresh.getId() : fresh.getCode());
            }
        }
        return leveled;
    }

    private List<Map<String, Object>> collectBillingSnapshots() {
        List<Map<String, Object>> snapshots = new ArrayList<>();
        for (StudentSchedule schedule : studentScheduleRepository.findByTerm(currentTerm)) {
            List<String> enrolledIds = scheduleItemRepository.findByScheduleId(schedule.getId()).stream()
                    .filter(item -> item.getStatus() == ItemStatus.enrolled)
                    .map(ScheduleItemEntity::getOfferingId)
                    .toList();
            if (enrolledIds.isEmpty()) {
                continue;
            }
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("studentId", schedule.getStudentId());
            snapshot.put("term", currentTerm);
            snapshot.put("enrolledOfferingIds", enrolledIds);
            snapshots.add(snapshot);
        }
        return snapshots;
    }

    private Set<String> completedCourseCodes(String studentId) {
        Set<String> codes = new HashSet<>();
        Map<String, CourseOffering> all = courseOfferingRepository.findAll().stream()
                .collect(Collectors.toMap(CourseOffering::getId, Function.identity(), (a, b) -> a));
        for (Enrollment enrollment : enrollmentRepository.findByStudentId(studentId)) {
            if (currentTerm.equals(enrollment.getTerm())) {
                continue;
            }
            if (enrollment.getGrade() == null || !PASSING_GRADES.contains(enrollment.getGrade())) {
                continue;
            }
            CourseOffering offering = all.get(enrollment.getOfferingId());
            codes.add(offering == null ? enrollment.getOfferingId() : offering.getCode());
        }
        return codes;
    }

    private boolean overlap(CourseOffering a, CourseOffering b) {
        List<String> daysA = parseList(a.getDaysJson());
        List<String> daysB = parseList(b.getDaysJson());
        boolean sameDay = daysA.stream().anyMatch(daysB::contains);
        if (!sameDay) {
            return false;
        }
        int startA = a.getStartMinute() == null ? 0 : a.getStartMinute();
        int endA = a.getEndMinute() == null ? 0 : a.getEndMinute();
        int startB = b.getStartMinute() == null ? 0 : b.getStartMinute();
        int endB = b.getEndMinute() == null ? 0 : b.getEndMinute();
        return startA < endB && startB < endA;
    }

    private List<String> parseList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private static int seatsTaken(CourseOffering offering) {
        return offering.getSeatsTaken() == null ? 0 : offering.getSeatsTaken();
    }

    private static int seatsTotal(CourseOffering offering) {
        return offering.getSeatsTotal() == null ? 0 : offering.getSeatsTotal();
    }

    /**
     * 编号规则：S/P + 至少三位数字。序号单调递增，删除后不回退，避免撞号。
     */
    private String allocateId(String prefix, List<String> existingIds) {
        IdSequence sequence = idSequenceRepository.findById(prefix).orElseGet(() -> {
            IdSequence created = new IdSequence();
            created.setPrefix(prefix);
            created.setLastValue(maxNumericSuffix(existingIds, prefix));
            return created;
        });
        sequence.setLastValue(sequence.getLastValue() + 1);
        idSequenceRepository.save(sequence);
        return prefix + String.format("%03d", sequence.getLastValue());
    }

    static int maxNumericSuffix(List<String> existingIds, String prefix) {
        int max = 0;
        for (String id : existingIds) {
            if (id == null || !id.startsWith(prefix)) {
                continue;
            }
            try {
                max = Math.max(max, Integer.parseInt(id.substring(prefix.length())));
            } catch (NumberFormatException ignored) {
                // 忽略非数字后缀
            }
        }
        return max;
    }

    /**
     * 新增师生时同步创建登录账号。用户名=系统编号；初始密码与编号相同（仅课程演示环境）。
     */
    private void createLoginAccount(String id, String name, Role role) {
        if (userAccountRepository.existsById(id) || userAccountRepository.findByUsername(id).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "无法创建登录账号：编号已被占用");
        }
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setUsername(id);
        user.setPasswordHash(passwordEncoder.encode(id));
        user.setName(name);
        user.setRole(role);
        userAccountRepository.save(user);
    }

    private void syncAccountName(String id, String name) {
        userAccountRepository.findById(id).ifPresent(user -> {
            user.setName(name);
            userAccountRepository.save(user);
        });
    }

    private void applyStudent(Student student, StudentUpsertRequest request) {
        student.setName(request.name());
        student.setDob(request.dob());
        student.setSsn(request.ssn());
        student.setStatus(request.status());
        student.setGraduationDate(request.graduationDate());
    }

    private void applyProfessor(Professor professor, ProfessorUpsertRequest request) {
        professor.setName(request.name());
        professor.setDob(request.dob());
        professor.setSsn(request.ssn());
        professor.setStatus(request.status());
        professor.setDept(request.dept());
    }

    private StudentVO toStudentVo(Student s) {
        return new StudentVO(s.getId(), s.getName(), s.getDob(), s.getSsn(), s.getStatus(), s.getGraduationDate());
    }

    private ProfessorVO toProfessorVo(Professor p) {
        return new ProfessorVO(p.getId(), p.getName(), p.getDob(), p.getSsn(), p.getStatus(), p.getDept());
    }
}
