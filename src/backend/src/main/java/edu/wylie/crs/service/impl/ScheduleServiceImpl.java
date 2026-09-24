package edu.wylie.crs.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.wylie.crs.common.ApiException;
import edu.wylie.crs.common.ErrorItem;
import edu.wylie.crs.domain.ItemStatus;
import edu.wylie.crs.domain.ItemType;
import edu.wylie.crs.domain.ScheduleStatus;
import edu.wylie.crs.dto.ScheduleDtos.ScheduleItemVO;
import edu.wylie.crs.dto.ScheduleDtos.ScheduleVO;
import edu.wylie.crs.entity.CourseOffering;
import edu.wylie.crs.entity.Enrollment;
import edu.wylie.crs.entity.ScheduleItemEntity;
import edu.wylie.crs.entity.StudentSchedule;
import edu.wylie.crs.entity.TermConfig;
import edu.wylie.crs.repository.CourseOfferingRepository;
import edu.wylie.crs.repository.EnrollmentRepository;
import edu.wylie.crs.repository.ScheduleItemRepository;
import edu.wylie.crs.repository.StudentScheduleRepository;
import edu.wylie.crs.repository.TermConfigRepository;
import edu.wylie.crs.security.AuthHolder;
import edu.wylie.crs.security.AuthUser;
import edu.wylie.crs.service.ScheduleService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ScheduleServiceImpl implements ScheduleService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final int MAX_PRIMARY = 4;
    private static final int MAX_ALTERNATE = 2;
    private static final Set<String> PASSING_GRADES = Set.of("A", "B", "C", "D");

    private final StudentScheduleRepository studentScheduleRepository;
    private final ScheduleItemRepository scheduleItemRepository;
    private final CourseOfferingRepository courseOfferingRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final TermConfigRepository termConfigRepository;
    private final ObjectMapper objectMapper;
    private final String currentTerm;

    public ScheduleServiceImpl(
            StudentScheduleRepository studentScheduleRepository,
            ScheduleItemRepository scheduleItemRepository,
            CourseOfferingRepository courseOfferingRepository,
            EnrollmentRepository enrollmentRepository,
            TermConfigRepository termConfigRepository,
            ObjectMapper objectMapper,
            @Value("${crs.term.current}") String currentTerm
    ) {
        this.studentScheduleRepository = studentScheduleRepository;
        this.scheduleItemRepository = scheduleItemRepository;
        this.courseOfferingRepository = courseOfferingRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.termConfigRepository = termConfigRepository;
        this.objectMapper = objectMapper;
        this.currentTerm = currentTerm;
    }

    @Override
    @Transactional(readOnly = true)
    public ScheduleVO getMine() {
        return studentScheduleRepository.findByStudentIdAndTerm(currentStudentId(), currentTerm)
                .map(this::toVo)
                .orElseGet(() -> new ScheduleVO(ScheduleStatus.draft, List.of(), null));
    }

    @Override
    @Transactional
    public ScheduleVO save(List<ScheduleItemVO> items) {
        assertRegistrationOpen("无法修改课表");
        List<ScheduleItemVO> incoming = items == null ? List.of() : items;
        List<ResolvedItem> resolved = resolveAndValidateStructure(incoming, "请修正后重新保存");

        String studentId = currentStudentId();
        StudentSchedule schedule = getOrCreate(studentId);
        releaseEnrolledExcept(studentId, schedule, Set.of());
        replaceItems(schedule, resolved, ItemStatus.selected, ItemStatus.selected);

        schedule.setStatus(ScheduleStatus.saved);
        schedule.setSubmitTime(null);
        studentScheduleRepository.save(schedule);
        return toVo(schedule);
    }

    @Override
    @Transactional
    public ScheduleVO submit(List<ScheduleItemVO> items) {
        assertRegistrationOpen("无法提交课表");
        List<ScheduleItemVO> incoming = items == null ? List.of() : items;
        List<ResolvedItem> resolved = resolveAndValidateStructure(incoming, "请修正后重新提交");

        List<ErrorItem> errors = new ArrayList<>();
        Set<String> completed = completedCourseCodes(currentStudentId());
        for (ResolvedItem item : resolved) {
            if (item.vo().type() != ItemType.primary) {
                continue;
            }
            CourseOffering offering = item.offering();
            if (offering.isCancelled() || offering.isOfferingClosed()) {
                errors.add(new ErrorItem(
                        offering.getId(),
                        "conflict",
                        offering.getCode() + " 已关闭或已取消，不能提交"
                ));
                continue;
            }
            List<String> prereqs = parseList(offering.getPrerequisitesJson());
            List<String> missing = prereqs.stream().filter(code -> !completed.contains(code)).toList();
            if (!missing.isEmpty()) {
                errors.add(new ErrorItem(
                        offering.getId(),
                        "prerequisite",
                        offering.getCode() + " 未满足先修课要求（" + String.join("、", missing) + "）"
                ));
            }
            int taken = offering.getSeatsTaken() == null ? 0 : offering.getSeatsTaken();
            int total = offering.getSeatsTotal() == null ? 0 : offering.getSeatsTotal();
            if (taken >= total) {
                errors.add(new ErrorItem(offering.getId(), "full", offering.getCode() + " 名额已满"));
            }
        }
        throwIfErrors(errors, "课表校验未通过，请修正后重新提交");

        String studentId = currentStudentId();
        StudentSchedule schedule = getOrCreate(studentId);
        Set<String> newPrimaryIds = resolved.stream()
                .filter(item -> item.vo().type() == ItemType.primary)
                .map(item -> item.vo().offeringId())
                .collect(Collectors.toCollection(HashSet::new));
        releaseEnrolledExcept(studentId, schedule, newPrimaryIds);
        occupyNewPrimaries(studentId, newPrimaryIds);
        replaceItems(schedule, resolved, ItemStatus.enrolled, ItemStatus.selected);

        schedule.setStatus(ScheduleStatus.submitted);
        schedule.setSubmitTime(Instant.now());
        studentScheduleRepository.save(schedule);
        return toVo(schedule);
    }

    @Override
    @Transactional
    public void deleteMine() {
        assertRegistrationOpen("无法删除课表");
        String studentId = currentStudentId();
        StudentSchedule schedule = studentScheduleRepository.findByStudentIdAndTerm(studentId, currentTerm)
                .orElse(null);
        if (schedule == null) {
            return;
        }
        releaseEnrolledExcept(studentId, schedule, Set.of());
        scheduleItemRepository.deleteByScheduleId(schedule.getId());
        studentScheduleRepository.delete(schedule);
    }

    private List<ResolvedItem> resolveAndValidateStructure(List<ScheduleItemVO> incoming, String actionHint) {
        List<ErrorItem> errors = new ArrayList<>();
        List<ResolvedItem> resolved = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int primaryCount = 0;
        int alternateCount = 0;

        Map<String, CourseOffering> offerings = courseOfferingRepository.findByTerm(currentTerm).stream()
                .collect(Collectors.toMap(CourseOffering::getId, Function.identity()));

        for (ScheduleItemVO item : incoming) {
            if (item == null || item.offeringId() == null || item.offeringId().isBlank()) {
                errors.add(new ErrorItem("", "conflict", "课表条目缺少课程编号"));
                continue;
            }
            if (item.type() == null) {
                errors.add(new ErrorItem(item.offeringId(), "conflict", "课表条目缺少主选/备选类型"));
                continue;
            }
            if (!seen.add(item.offeringId())) {
                errors.add(new ErrorItem(item.offeringId(), "conflict", item.offeringId() + " 不能重复选择"));
                continue;
            }
            if (item.type() == ItemType.primary) {
                primaryCount++;
            } else {
                alternateCount++;
            }
            CourseOffering offering = offerings.get(item.offeringId());
            if (offering == null) {
                errors.add(new ErrorItem(item.offeringId(), "conflict", item.offeringId() + " 不在本学期开课目录中"));
                continue;
            }
            resolved.add(new ResolvedItem(item, offering));
        }

        if (primaryCount > MAX_PRIMARY) {
            errors.add(new ErrorItem("", "conflict", "主选课程不能超过 4 门"));
        }
        if (alternateCount > MAX_ALTERNATE) {
            errors.add(new ErrorItem("", "conflict", "备选课程不能超过 2 门"));
        }
        errors.addAll(timeConflicts(resolved));
        throwIfErrors(errors, "课表校验未通过，" + actionHint);
        return assignPriority(resolved);
    }

    private List<ErrorItem> timeConflicts(List<ResolvedItem> items) {
        List<ErrorItem> errors = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            for (int j = i + 1; j < items.size(); j++) {
                ResolvedItem a = items.get(i);
                ResolvedItem b = items.get(j);
                if (!overlap(a.offering(), b.offering())) {
                    continue;
                }
                String ta = a.vo().type() == ItemType.primary ? "主选" : "备选";
                String tb = b.vo().type() == ItemType.primary ? "主选" : "备选";
                errors.add(new ErrorItem(
                        b.offering().getId(),
                        "conflict",
                        b.offering().getCode() + "（" + tb + "）与 " + a.offering().getCode() + "（" + ta + "）上课时间冲突"
                ));
            }
        }
        return errors;
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

    private List<ResolvedItem> assignPriority(List<ResolvedItem> items) {
        int priority = 0;
        List<ResolvedItem> result = new ArrayList<>(items.size());
        for (ResolvedItem item : items) {
            if (item.vo().type() == ItemType.alternate) {
                priority++;
                result.add(new ResolvedItem(
                        new ScheduleItemVO(item.vo().offeringId(), ItemType.alternate, item.vo().status(), priority),
                        item.offering()
                ));
            } else {
                result.add(new ResolvedItem(
                        new ScheduleItemVO(item.vo().offeringId(), ItemType.primary, item.vo().status(), null),
                        item.offering()
                ));
            }
        }
        return result;
    }

    /**
     * 释放旧课表中已 enrolled、且不在保留集合内的名额（条件更新 seats_taken）。
     * 暂存传入空集合，表示全部释放；提交传入新主选集合，只释放被换掉的课。
     */
    private void releaseEnrolledExcept(String studentId, StudentSchedule schedule, Set<String> keepPrimaryIds) {
        for (ScheduleItemEntity item : scheduleItemRepository.findByScheduleId(schedule.getId())) {
            if (item.getStatus() != ItemStatus.enrolled) {
                continue;
            }
            if (keepPrimaryIds.contains(item.getOfferingId())) {
                continue;
            }
            courseOfferingRepository.decrementSeat(item.getOfferingId());
            enrollmentRepository.deleteByOfferingIdAndStudentId(item.getOfferingId(), studentId);
        }
    }

    private void occupyNewPrimaries(String studentId, Set<String> newPrimaryIds) {
        List<String> ordered = newPrimaryIds.stream().sorted().toList();
        for (String offeringId : ordered) {
            if (enrollmentRepository.existsByOfferingIdAndStudentId(offeringId, studentId)) {
                continue;
            }
            int updated = courseOfferingRepository.incrementSeatIfAvailable(offeringId);
            if (updated == 0) {
                CourseOffering offering = courseOfferingRepository.findById(offeringId).orElse(null);
                String code = offering == null ? offeringId : offering.getCode();
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "课表校验未通过，请修正后重新提交：" + code + " 名额已满",
                        List.of(new ErrorItem(offeringId, "full", code + " 名额已满"))
                );
            }
            Enrollment enrollment = new Enrollment();
            enrollment.setOfferingId(offeringId);
            enrollment.setStudentId(studentId);
            enrollment.setTerm(currentTerm);
            enrollmentRepository.save(enrollment);
        }
    }

    private void replaceItems(
            StudentSchedule schedule,
            List<ResolvedItem> resolved,
            ItemStatus primaryStatus,
            ItemStatus alternateStatus
    ) {
        scheduleItemRepository.deleteByScheduleId(schedule.getId());
        for (ResolvedItem item : resolved) {
            ScheduleItemEntity entity = new ScheduleItemEntity();
            entity.setScheduleId(schedule.getId());
            entity.setOfferingId(item.vo().offeringId());
            entity.setType(item.vo().type());
            entity.setStatus(item.vo().type() == ItemType.primary ? primaryStatus : alternateStatus);
            entity.setPriority(item.vo().type() == ItemType.alternate ? item.vo().priority() : null);
            scheduleItemRepository.save(entity);
        }
    }

    private StudentSchedule getOrCreate(String studentId) {
        return studentScheduleRepository.findByStudentIdAndTerm(studentId, currentTerm)
                .orElseGet(() -> {
                    StudentSchedule created = new StudentSchedule();
                    created.setStudentId(studentId);
                    created.setTerm(currentTerm);
                    created.setStatus(ScheduleStatus.draft);
                    return studentScheduleRepository.save(created);
                });
    }

    private Set<String> completedCourseCodes(String studentId) {
        Set<String> codes = new HashSet<>();
        Map<String, CourseOffering> offerings = courseOfferingRepository.findAll().stream()
                .collect(Collectors.toMap(CourseOffering::getId, Function.identity(), (a, b) -> a));
        for (Enrollment enrollment : enrollmentRepository.findByStudentId(studentId)) {
            if (currentTerm.equals(enrollment.getTerm())) {
                continue;
            }
            if (enrollment.getGrade() == null || !PASSING_GRADES.contains(enrollment.getGrade())) {
                continue;
            }
            CourseOffering offering = offerings.get(enrollment.getOfferingId());
            codes.add(offering == null ? enrollment.getOfferingId() : offering.getCode());
        }
        return codes;
    }

    private ScheduleVO toVo(StudentSchedule schedule) {
        List<ScheduleItemVO> items = scheduleItemRepository.findByScheduleId(schedule.getId()).stream()
                .map(item -> new ScheduleItemVO(
                        item.getOfferingId(),
                        item.getType(),
                        item.getStatus(),
                        item.getPriority()
                ))
                .toList();
        String submitTime = schedule.getSubmitTime() == null ? null : schedule.getSubmitTime().toString();
        return new ScheduleVO(schedule.getStatus(), items, submitTime);
    }

    private void assertRegistrationOpen(String action) {
        TermConfig config = termConfigRepository.findById(currentTerm)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "当前学期未配置"));
        if (config.isRegistrationClosed()) {
            throw new ApiException(HttpStatus.CONFLICT, "本学期注册已关闭，" + action);
        }
    }

    private String currentStudentId() {
        AuthUser user = AuthHolder.get();
        if (user == null || user.id() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        return user.id();
    }

    private void throwIfErrors(List<ErrorItem> errors, String message) {
        if (errors.isEmpty()) {
            return;
        }
        String details = errors.stream()
                .map(ErrorItem::message)
                .filter(item -> item != null && !item.isBlank())
                .distinct()
                .collect(Collectors.joining("；"));
        String full = details.isEmpty() ? message : message + "：" + details;
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, full, errors);
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

    private record ResolvedItem(ScheduleItemVO vo, CourseOffering offering) {
    }
}
