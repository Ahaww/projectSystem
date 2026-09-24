package edu.wylie.crs.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.wylie.crs.common.ApiException;
import edu.wylie.crs.common.ErrorItem;
import edu.wylie.crs.dto.OfferingVO;
import edu.wylie.crs.entity.CourseOffering;
import edu.wylie.crs.entity.Professor;
import edu.wylie.crs.entity.TeachingAssignment;
import edu.wylie.crs.entity.TermConfig;
import edu.wylie.crs.integration.CourseCatalogClient;
import edu.wylie.crs.repository.CourseOfferingRepository;
import edu.wylie.crs.repository.ProfessorRepository;
import edu.wylie.crs.repository.TeachingAssignmentRepository;
import edu.wylie.crs.repository.TermConfigRepository;
import edu.wylie.crs.security.AuthHolder;
import edu.wylie.crs.security.AuthUser;
import edu.wylie.crs.service.CatalogCacheService;
import edu.wylie.crs.service.TeachingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TeachingServiceImpl implements TeachingService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final TeachingAssignmentRepository teachingAssignmentRepository;
    private final CourseOfferingRepository courseOfferingRepository;
    private final ProfessorRepository professorRepository;
    private final TermConfigRepository termConfigRepository;
    private final CatalogCacheService catalogCacheService;
    private final CourseCatalogClient courseCatalogClient;
    private final ObjectMapper objectMapper;
    private final String currentTerm;

    public TeachingServiceImpl(
            TeachingAssignmentRepository teachingAssignmentRepository,
            CourseOfferingRepository courseOfferingRepository,
            ProfessorRepository professorRepository,
            TermConfigRepository termConfigRepository,
            CatalogCacheService catalogCacheService,
            CourseCatalogClient courseCatalogClient,
            ObjectMapper objectMapper,
            @Value("${crs.term.current}") String currentTerm
    ) {
        this.teachingAssignmentRepository = teachingAssignmentRepository;
        this.courseOfferingRepository = courseOfferingRepository;
        this.professorRepository = professorRepository;
        this.termConfigRepository = termConfigRepository;
        this.catalogCacheService = catalogCacheService;
        this.courseCatalogClient = courseCatalogClient;
        this.objectMapper = objectMapper;
        this.currentTerm = currentTerm;
    }

    @Override
    @Transactional
    public List<OfferingVO> eligible() {
        catalogCacheService.syncCurrentTerm();
        List<CourseOffering> cached = courseOfferingRepository.findByTerm(currentTerm);
        if (cached.isEmpty() && !courseCatalogClient.isAvailable()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "课程目录系统不可用");
        }

        Professor professor = currentProfessor();
        Set<String> teachingIds = teachingAssignmentRepository
                .findByProfessorIdAndTerm(professor.getId(), currentTerm)
                .stream()
                .map(TeachingAssignment::getOfferingId)
                .collect(Collectors.toSet());

        String dept = normalize(professor.getDept());
        return cached.stream()
                .filter(row -> !row.isCancelled())
                .filter(row -> dept.equals(normalize(row.getDept())))
                .sorted(Comparator.comparing(CourseOffering::getId, Comparator.nullsLast(String::compareTo)))
                .map(row -> toVo(row, teachingIds.contains(row.getId())))
                .toList();
    }

    @Override
    @Transactional
    public void updateTeaching(List<String> offeringIds) {
        assertRegistrationOpen();
        Professor professor = currentProfessor();
        List<String> requested = dedupe(offeringIds == null ? List.of() : offeringIds);

        Map<String, CourseOffering> offerings = courseOfferingRepository.findAllById(requested).stream()
                .collect(Collectors.toMap(CourseOffering::getId, Function.identity()));
        List<CourseOffering> selected = new ArrayList<>(requested.size());
        for (String id : requested) {
            CourseOffering offering = offerings.get(id);
            if (offering == null || !currentTerm.equals(offering.getTerm()) || offering.isCancelled()) {
                throw new ApiException(HttpStatus.NOT_FOUND, "班次不存在：" + id);
            }
            if (!normalize(professor.getDept()).equals(normalize(offering.getDept()))) {
                throw new ApiException(HttpStatus.CONFLICT, "无权任教该班次：" + id);
            }
            selected.add(offering);
        }

        List<ErrorItem> conflicts = timeConflicts(selected);
        if (!conflicts.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "所选课程存在时间冲突", conflicts);
        }

        for (CourseOffering offering : selected) {
            List<TeachingAssignment> holders =
                    teachingAssignmentRepository.findByOfferingIdAndTerm(offering.getId(), currentTerm);
            for (TeachingAssignment holder : holders) {
                if (!professor.getId().equals(holder.getProfessorId())) {
                    throw new ApiException(
                            HttpStatus.CONFLICT,
                            offering.getCode() + " 已由其他教授任教，不能覆盖",
                            List.of(new ErrorItem(
                                    offering.getId(),
                                    "conflict",
                                    offering.getCode() + " 已由其他教授任教，不能覆盖"
                            ))
                    );
                }
            }
        }

        Set<String> newIds = new LinkedHashSet<>(requested);
        List<TeachingAssignment> previous =
                teachingAssignmentRepository.findByProfessorIdAndTerm(professor.getId(), currentTerm);
        Set<String> previousIds = previous.stream()
                .map(TeachingAssignment::getOfferingId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        teachingAssignmentRepository.deleteByProfessorIdAndTerm(professor.getId(), currentTerm);

        for (String releasedId : previousIds) {
            if (newIds.contains(releasedId)) {
                continue;
            }
            courseOfferingRepository.findById(releasedId).ifPresent(offering -> {
                if (professor.getId().equals(offering.getProfessorId())) {
                    offering.setProfessorId(null);
                    courseOfferingRepository.save(offering);
                }
            });
        }

        for (CourseOffering offering : selected) {
            TeachingAssignment assignment = new TeachingAssignment();
            assignment.setProfessorId(professor.getId());
            assignment.setOfferingId(offering.getId());
            assignment.setTerm(currentTerm);
            teachingAssignmentRepository.save(assignment);

            offering.setProfessorId(professor.getId());
            offering.setProfessorName(professor.getName());
            courseOfferingRepository.save(offering);
        }
    }

    private List<ErrorItem> timeConflicts(List<CourseOffering> selected) {
        List<ErrorItem> errors = new ArrayList<>();
        for (int i = 0; i < selected.size(); i++) {
            for (int j = i + 1; j < selected.size(); j++) {
                CourseOffering a = selected.get(i);
                CourseOffering b = selected.get(j);
                if (!overlap(a, b)) {
                    continue;
                }
                errors.add(new ErrorItem(
                        a.getId(),
                        "conflict",
                        a.getCode() + " 与 " + b.getCode() + " 时间冲突"
                ));
                errors.add(new ErrorItem(
                        b.getId(),
                        "conflict",
                        b.getCode() + " 与 " + a.getCode() + " 时间冲突"
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

    private OfferingVO toVo(CourseOffering row, boolean teaching) {
        return new OfferingVO(
                row.getId(),
                row.getCode(),
                row.getTitle(),
                row.getDept(),
                row.getProfessorName() == null ? "" : row.getProfessorName(),
                parseList(row.getDaysJson()),
                row.getStartMinute() == null ? 0 : row.getStartMinute(),
                row.getEndMinute() == null ? 0 : row.getEndMinute(),
                row.getRoom(),
                row.getSeatsTotal() == null ? 0 : row.getSeatsTotal(),
                row.getSeatsTaken() == null ? 0 : row.getSeatsTaken(),
                parseList(row.getPrerequisitesJson()),
                teaching,
                row.isCancelled() ? Boolean.TRUE : null
        );
    }

    private void assertRegistrationOpen() {
        TermConfig config = termConfigRepository.findById(currentTerm)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "当前学期未配置"));
        if (config.isRegistrationClosed()) {
            throw new ApiException(HttpStatus.CONFLICT, "本学期注册已关闭，不能再变更授课课程");
        }
    }

    private Professor currentProfessor() {
        AuthUser user = AuthHolder.get();
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "未登录");
        }
        return professorRepository.findById(user.id())
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "当前账号不是有效教授"));
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

    private static List<String> dedupe(List<String> ids) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                unique.add(id.trim());
            }
        }
        return List.copyOf(unique);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
