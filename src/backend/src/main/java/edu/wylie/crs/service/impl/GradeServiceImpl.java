package edu.wylie.crs.service.impl;

import edu.wylie.crs.common.ApiException;
import edu.wylie.crs.domain.GradeCode;
import edu.wylie.crs.dto.GradeDtos.GradeEntry;
import edu.wylie.crs.dto.GradeDtos.ReportCardItemVO;
import edu.wylie.crs.dto.GradeDtos.ReportCardVO;
import edu.wylie.crs.dto.GradeDtos.RosterRowVO;
import edu.wylie.crs.dto.GradeDtos.SaveGradesRequest;
import edu.wylie.crs.dto.GradeDtos.SemesterVO;
import edu.wylie.crs.dto.GradeDtos.TaughtOfferingVO;
import edu.wylie.crs.entity.CourseOffering;
import edu.wylie.crs.entity.Enrollment;
import edu.wylie.crs.entity.Student;
import edu.wylie.crs.entity.TermConfig;
import edu.wylie.crs.repository.CourseOfferingRepository;
import edu.wylie.crs.repository.EnrollmentRepository;
import edu.wylie.crs.repository.StudentRepository;
import edu.wylie.crs.repository.TermConfigRepository;
import edu.wylie.crs.security.AuthHolder;
import edu.wylie.crs.security.AuthUser;
import edu.wylie.crs.service.GradeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GradeServiceImpl implements GradeService {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseOfferingRepository courseOfferingRepository;
    private final StudentRepository studentRepository;
    private final TermConfigRepository termConfigRepository;
    private final String currentTerm;

    public GradeServiceImpl(
            EnrollmentRepository enrollmentRepository,
            CourseOfferingRepository courseOfferingRepository,
            StudentRepository studentRepository,
            TermConfigRepository termConfigRepository,
            @Value("${crs.term.current}") String currentTerm
    ) {
        this.enrollmentRepository = enrollmentRepository;
        this.courseOfferingRepository = courseOfferingRepository;
        this.studentRepository = studentRepository;
        this.termConfigRepository = termConfigRepository;
        this.currentTerm = currentTerm;
    }

    @Override
    @Transactional(readOnly = true)
    public ReportCardVO myReportCard() {
        String studentId = currentUser().id();
        List<Enrollment> enrollments = enrollmentRepository.findByStudentId(studentId);
        if (enrollments.isEmpty()) {
            return new ReportCardVO(List.of());
        }

        Map<String, CourseOffering> offerings = courseOfferingRepository.findAll().stream()
                .collect(Collectors.toMap(CourseOffering::getId, o -> o, (a, b) -> a));

        Map<String, List<ReportCardItemVO>> byTerm = new LinkedHashMap<>();
        enrollments.stream()
                .sorted(Comparator.comparing(Enrollment::getTerm, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Enrollment::getOfferingId, Comparator.nullsLast(String::compareTo)))
                .forEach(enrollment -> {
                    CourseOffering offering = offerings.get(enrollment.getOfferingId());
                    String code = offering == null ? enrollment.getOfferingId() : offering.getCode();
                    String title = offering == null ? enrollment.getOfferingId() : offering.getTitle();
                    int credits = offering == null || offering.getCredits() == null ? 3 : offering.getCredits();
                    String term = enrollment.getTerm() == null ? "" : enrollment.getTerm();
                    byTerm.computeIfAbsent(term, k -> new ArrayList<>())
                            .add(new ReportCardItemVO(code, title, credits, enrollment.getGrade()));
                });

        List<SemesterVO> semesters = byTerm.entrySet().stream()
                .map(e -> new SemesterVO(e.getKey(), e.getValue()))
                .toList();
        return new ReportCardVO(semesters);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaughtOfferingVO> taughtOfferings() {
        String professorId = currentUser().id();
        return courseOfferingRepository.findByProfessorId(professorId).stream()
                .filter(offering -> !offering.isCancelled())
                .filter(this::isTermClosed)
                .sorted(Comparator.comparing(CourseOffering::getTerm, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(CourseOffering::getId))
                .map(offering -> new TaughtOfferingVO(
                        offering.getId(),
                        offering.getTitle() + "（" + offering.getCode() + "）",
                        offering.getTerm(),
                        enrollmentRepository.findByOfferingId(offering.getId()).size()
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RosterRowVO> roster(String offeringId) {
        CourseOffering offering = requireOwnOffering(offeringId);
        denyIfCurrentTermStillOpen(offering);

        Map<String, String> names = studentRepository.findAll().stream()
                .collect(Collectors.toMap(Student::getId, Student::getName, (a, b) -> a));

        return enrollmentRepository.findByOfferingId(offeringId).stream()
                .sorted(Comparator.comparing(Enrollment::getStudentId))
                .map(enrollment -> new RosterRowVO(
                        enrollment.getStudentId(),
                        names.getOrDefault(enrollment.getStudentId(), enrollment.getStudentId()),
                        enrollment.getGrade()
                ))
                .toList();
    }

    @Override
    @Transactional
    public void saveGrades(String offeringId, SaveGradesRequest request) {
        CourseOffering offering = requireOwnOffering(offeringId);
        denyIfCurrentTermStillOpen(offering);

        List<GradeEntry> entries = request == null || request.grades() == null ? List.of() : request.grades();
        Map<String, Enrollment> byStudent = enrollmentRepository.findByOfferingId(offeringId).stream()
                .collect(Collectors.toMap(Enrollment::getStudentId, e -> e, (a, b) -> a));

        for (GradeEntry entry : entries) {
            if (entry == null || entry.studentId() == null || entry.studentId().isBlank()) {
                continue;
            }
            Enrollment enrollment = byStudent.get(entry.studentId());
            if (enrollment == null) {
                continue;
            }
            enrollment.setGrade(normalizeGrade(entry.grade()));
            enrollmentRepository.save(enrollment);
        }
    }

    private CourseOffering requireOwnOffering(String offeringId) {
        CourseOffering offering = courseOfferingRepository.findById(offeringId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "课程不存在"));
        if (!currentUser().id().equals(offering.getProfessorId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "只能查看或录入本人所授班次的成绩");
        }
        return offering;
    }

    private void denyIfCurrentTermStillOpen(CourseOffering offering) {
        if (currentTerm.equals(offering.getTerm()) && !isTermClosed(offering)) {
            throw new ApiException(HttpStatus.CONFLICT, "注册尚未关闭，暂不可查看选课名册");
        }
    }

    private boolean isTermClosed(CourseOffering offering) {
        return isTermClosed(offering.getTerm());
    }

    private boolean isTermClosed(String term) {
        return termConfigRepository.findById(term)
                .map(TermConfig::isRegistrationClosed)
                .orElse(false);
    }

    private String normalizeGrade(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String grade = raw.trim().toUpperCase(Locale.ROOT);
        try {
            GradeCode.valueOf(grade);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "非法成绩：" + raw + "，允许 A/B/C/D/F/I 或留空");
        }
        return grade;
    }

    private AuthUser currentUser() {
        AuthUser user = AuthHolder.get();
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        return user;
    }
}
