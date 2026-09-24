package edu.wylie.crs.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.wylie.crs.dto.OfferingVO;
import edu.wylie.crs.entity.CourseOffering;
import edu.wylie.crs.entity.Professor;
import edu.wylie.crs.integration.CourseCatalogClient;
import edu.wylie.crs.repository.CourseOfferingRepository;
import edu.wylie.crs.repository.ProfessorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * 将遗留课程目录只读快照 upsert 到本地 {@code course_offering}。
 * 不回写遗留库；不覆盖本库运行时状态（座位占用、任教、取消/关闭标记）。
 */
@Service
@Order(2)
public class CatalogCacheService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogCacheService.class);

    /** 目录显示名与本库教授档案不完全一致时的别名 */
    private static final Map<String, String> PROFESSOR_NAME_ALIASES = Map.of(
            "Morgan 博士", "P001"
    );

    /** 目录 Stub 无学分字段时按课程代码补齐，供成绩单使用 */
    private static final Map<String, Integer> CREDITS_BY_CODE = Map.ofEntries(
            Map.entry("CS-301", 3),
            Map.entry("MATH-214", 3),
            Map.entry("BUS-110", 3),
            Map.entry("CS-401", 3),
            Map.entry("PHYS-150", 4),
            Map.entry("HIST-101", 3),
            Map.entry("ART-120", 2),
            Map.entry("CS-201", 3),
            Map.entry("CS-350", 3),
            Map.entry("MATH-301", 4),
            Map.entry("ENG-201", 2),
            Map.entry("CHEM-101", 4),
            Map.entry("ECON-210", 3)
    );

    private final CourseCatalogClient courseCatalogClient;
    private final CourseOfferingRepository courseOfferingRepository;
    private final ProfessorRepository professorRepository;
    private final ObjectMapper objectMapper;
    private final String currentTerm;

    public CatalogCacheService(
            CourseCatalogClient courseCatalogClient,
            CourseOfferingRepository courseOfferingRepository,
            ProfessorRepository professorRepository,
            ObjectMapper objectMapper,
            @Value("${crs.term.current}") String currentTerm
    ) {
        this.courseCatalogClient = courseCatalogClient;
        this.courseOfferingRepository = courseOfferingRepository;
        this.professorRepository = professorRepository;
        this.objectMapper = objectMapper;
        this.currentTerm = currentTerm;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        syncCurrentTerm();
    }

    @Transactional
    public void syncCurrentTerm() {
        if (!courseCatalogClient.isAvailable()) {
            log.warn("课程目录不可用，跳过同步，继续使用本地缓存");
            return;
        }
        int upserted = 0;
        for (OfferingVO vo : courseCatalogClient.listCurrentTermOfferings(currentTerm)) {
            upsertFromCatalog(vo);
            upserted++;
        }
        log.info("课程目录已同步到本地缓存：term={}，班次={}", currentTerm, upserted);
    }

    private void upsertFromCatalog(OfferingVO vo) {
        Optional<CourseOffering> existing = courseOfferingRepository.findById(vo.id());
        CourseOffering row = existing.orElseGet(CourseOffering::new);
        boolean isNew = existing.isEmpty();

        row.setId(vo.id());
        row.setCode(vo.code());
        row.setTitle(vo.title());
        row.setDept(vo.dept());
        row.setDaysJson(toJson(vo.days()));
        row.setStartMinute(vo.start());
        row.setEndMinute(vo.end());
        row.setRoom(vo.room());
        row.setSeatsTotal(vo.seatsTotal());
        row.setPrerequisitesJson(toJson(vo.prerequisites()));
        row.setTerm(currentTerm);

        row.setCredits(CREDITS_BY_CODE.getOrDefault(vo.code(), 3));

        if (isNew) {
            row.setSeatsTaken(vo.seatsTaken());
            row.setCancelled(Boolean.TRUE.equals(vo.cancelled()));
            row.setOfferingClosed(false);
            row.setProfessorId(resolveProfessorId(vo.professor()));
            row.setProfessorName(vo.professor() == null ? "" : vo.professor());
        }

        courseOfferingRepository.save(row);
    }

    private String resolveProfessorId(String catalogName) {
        if (catalogName == null || catalogName.isBlank()) {
            return null;
        }
        String aliased = PROFESSOR_NAME_ALIASES.get(catalogName);
        if (aliased != null) {
            return aliased;
        }
        return professorRepository.findAll().stream()
                .filter(p -> catalogName.equals(p.getName()))
                .map(Professor::getId)
                .findFirst()
                .orElse(null);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化目录字段失败", e);
        }
    }
}
