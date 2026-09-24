package edu.wylie.crs.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.wylie.crs.common.ApiException;
import edu.wylie.crs.domain.Role;
import edu.wylie.crs.dto.OfferingVO;
import edu.wylie.crs.dto.RegistrarDtos.RegistrationStatusVO;
import edu.wylie.crs.entity.CourseOffering;
import edu.wylie.crs.entity.Professor;
import edu.wylie.crs.entity.TermConfig;
import edu.wylie.crs.integration.CourseCatalogClient;
import edu.wylie.crs.repository.CourseOfferingRepository;
import edu.wylie.crs.repository.ProfessorRepository;
import edu.wylie.crs.repository.TermConfigRepository;
import edu.wylie.crs.security.AuthHolder;
import edu.wylie.crs.security.AuthUser;
import edu.wylie.crs.service.CatalogCacheService;
import edu.wylie.crs.service.OfferingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OfferingServiceImpl implements OfferingService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final TermConfigRepository termConfigRepository;
    private final CourseOfferingRepository courseOfferingRepository;
    private final ProfessorRepository professorRepository;
    private final CourseCatalogClient courseCatalogClient;
    private final CatalogCacheService catalogCacheService;
    private final ObjectMapper objectMapper;
    private final String currentTerm;

    public OfferingServiceImpl(
            TermConfigRepository termConfigRepository,
            CourseOfferingRepository courseOfferingRepository,
            ProfessorRepository professorRepository,
            CourseCatalogClient courseCatalogClient,
            CatalogCacheService catalogCacheService,
            ObjectMapper objectMapper,
            @Value("${crs.term.current}") String currentTerm
    ) {
        this.termConfigRepository = termConfigRepository;
        this.courseOfferingRepository = courseOfferingRepository;
        this.professorRepository = professorRepository;
        this.courseCatalogClient = courseCatalogClient;
        this.catalogCacheService = catalogCacheService;
        this.objectMapper = objectMapper;
        this.currentTerm = currentTerm;
    }

    @Override
    @Transactional
    public List<OfferingVO> listForStudent(String keyword, String dept) {
        boolean maskProfessor = shouldMaskProfessorForCurrentUser();
        return listCurrentTerm(keyword, dept, maskProfessor);
    }

    @Override
    @Transactional
    public List<OfferingVO> listForRegistrar() {
        return listCurrentTerm(null, null, false);
    }

    @Override
    @Transactional(readOnly = true)
    public RegistrationStatusVO status() {
        TermConfig config = currentTermConfig();
        return new RegistrationStatusVO(config.isRegistrationClosed(), config.getTerm());
    }

    private List<OfferingVO> listCurrentTerm(String keyword, String dept, boolean maskProfessor) {
        catalogCacheService.syncCurrentTerm();
        List<CourseOffering> cached = courseOfferingRepository.findByTerm(currentTerm);
        if (cached.isEmpty() && !courseCatalogClient.isAvailable()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "课程目录系统不可用");
        }
        Map<String, Professor> professors = professorRepository.findAll().stream()
                .collect(Collectors.toMap(Professor::getId, Function.identity()));
        String kw = normalize(keyword);
        String deptFilter = normalize(dept);
        return cached.stream()
                .sorted(Comparator.comparing(CourseOffering::getId, Comparator.nullsLast(String::compareTo)))
                .filter(row -> matchesDept(row, deptFilter))
                .filter(row -> matchesKeyword(row, kw, professors, maskProfessor))
                .map(row -> toVo(row, professors, maskProfessor))
                .toList();
    }

    private boolean shouldMaskProfessorForCurrentUser() {
        AuthUser user = AuthHolder.get();
        if (user == null || user.role() != Role.STUDENT) {
            return false;
        }
        return !currentTermConfig().isRegistrationClosed();
    }

    private TermConfig currentTermConfig() {
        return termConfigRepository.findById(currentTerm)
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "当前学期未配置"));
    }

    private boolean matchesDept(CourseOffering row, String deptFilter) {
        if (deptFilter.isEmpty()) {
            return true;
        }
        return normalize(row.getDept()).contains(deptFilter);
    }

    private boolean matchesKeyword(
            CourseOffering row,
            String keyword,
            Map<String, Professor> professors,
            boolean maskProfessor
    ) {
        if (keyword.isEmpty()) {
            return true;
        }
        if (contains(row.getCode(), keyword)
                || contains(row.getTitle(), keyword)
                || contains(row.getDept(), keyword)
                || contains(row.getRoom(), keyword)
                || contains(row.getId(), keyword)) {
            return true;
        }
        if (maskProfessor) {
            return false;
        }
        return contains(displayProfessor(row, professors), keyword);
    }

    private OfferingVO toVo(CourseOffering row, Map<String, Professor> professors, boolean maskProfessor) {
        String professor = maskProfessor ? "" : displayProfessor(row, professors);
        return new OfferingVO(
                row.getId(),
                row.getCode(),
                row.getTitle(),
                row.getDept(),
                professor,
                parseList(row.getDaysJson()),
                row.getStartMinute() == null ? 0 : row.getStartMinute(),
                row.getEndMinute() == null ? 0 : row.getEndMinute(),
                row.getRoom(),
                row.getSeatsTotal() == null ? 0 : row.getSeatsTotal(),
                row.getSeatsTaken() == null ? 0 : row.getSeatsTaken(),
                parseList(row.getPrerequisitesJson()),
                null,
                row.isCancelled() ? Boolean.TRUE : null
        );
    }

    private static String displayProfessor(CourseOffering row, Map<String, Professor> professors) {
        if (row.getProfessorName() != null && !row.getProfessorName().isBlank()) {
            return row.getProfessorName();
        }
        if (row.getProfessorId() != null) {
            Professor professor = professors.get(row.getProfessorId());
            if (professor != null && professor.getName() != null) {
                return professor.getName();
            }
        }
        return "";
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

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean contains(String value, String keyword) {
        return normalize(value).contains(keyword);
    }
}
