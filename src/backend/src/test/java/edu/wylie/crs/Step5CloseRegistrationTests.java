package edu.wylie.crs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.wylie.crs.domain.ItemStatus;
import edu.wylie.crs.domain.ItemType;
import edu.wylie.crs.entity.CourseOffering;
import edu.wylie.crs.entity.ScheduleItemEntity;
import edu.wylie.crs.entity.StudentSchedule;
import edu.wylie.crs.entity.TermConfig;
import edu.wylie.crs.repository.CourseOfferingRepository;
import edu.wylie.crs.repository.EnrollmentRepository;
import edu.wylie.crs.repository.ScheduleItemRepository;
import edu.wylie.crs.repository.StudentScheduleRepository;
import edu.wylie.crs.repository.TermConfigRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class Step5CloseRegistrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TermConfigRepository termConfigRepository;

    @Autowired
    private CourseOfferingRepository courseOfferingRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private StudentScheduleRepository studentScheduleRepository;

    @Autowired
    private ScheduleItemRepository scheduleItemRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void resetCloseRegistrationState() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            studentScheduleRepository.findByTerm("2026 秋季").forEach(schedule -> {
                scheduleItemRepository.deleteByScheduleId(schedule.getId());
                studentScheduleRepository.delete(schedule);
            });
            enrollmentRepository.findByStudentIdAndTerm("S001", "2026 秋季")
                    .forEach(enrollmentRepository::delete);
            restoreOffering("CS-301", "P001", "Morgan 博士", 8);
            restoreOffering("MATH-214", "P002", "Lin 教授", 10);
            restoreOffering("BUS-110", "P005", "Patel 博士", 4);
            restoreOffering("CS-401", "P003", "Chen 博士", 2);
            restoreOffering("PHYS-150", "P006", "Kim 教授", 6);
            restoreOffering("HIST-101", "P004", "Lee 博士", 5);
            restoreOffering("ART-120", "P007", "Dai 博士", 2);
            TermConfig config = termConfigRepository.findById("2026 秋季").orElseThrow();
            config.setRegistrationClosed(false);
            termConfigRepository.save(config);
        });
    }

    @Test
    void closeCancelsUnderMinEnrollmentAndDoesNotCancelStaffedLargeSections() throws Exception {
        String admin = loginToken("admin");
        mockMvc.perform(post("/api/registrar/close-registration").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelled", containsInAnyOrder("ART-120", "CS-401")))
                .andExpect(jsonPath("$.cancelled", not(hasItem("CS-301"))))
                .andExpect(jsonPath("$.billed").value(0))
                .andExpect(jsonPath("$.leveled.length()").value(0));

        assertThat(courseOfferingRepository.findById("ART-120").orElseThrow().isCancelled()).isTrue();
        assertThat(courseOfferingRepository.findById("CS-301").orElseThrow().isCancelled()).isFalse();
        assertThat(courseOfferingRepository.findById("CS-301").orElseThrow().isOfferingClosed()).isTrue();
        assertThat(termConfigRepository.findById("2026 秋季").orElseThrow().isRegistrationClosed()).isTrue();
    }

    @Test
    void closeCancelsOfferingsWithoutProfessor() throws Exception {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            CourseOffering hist = courseOfferingRepository.findById("HIST-101").orElseThrow();
            hist.setProfessorId(null);
            courseOfferingRepository.save(hist);
        });

        String admin = loginToken("admin");
        mockMvc.perform(post("/api/registrar/close-registration").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelled", hasItem("HIST-101")))
                .andExpect(jsonPath("$.cancelled", hasItem("ART-120")));

        assertThat(courseOfferingRepository.findById("HIST-101").orElseThrow().isCancelled()).isTrue();
    }

    @Test
    void levelsAlternateByPriorityWhenPrimaryCountBelowFour() throws Exception {
        String student = loginToken("student");
        mockMvc.perform(post("/api/schedules/me/submit")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemsJson(
                                primary("CS-301"),
                                alternate("PHYS-150"),
                                alternate("ART-120"))))
                .andExpect(status().isOk());

        String admin = loginToken("admin");
        mockMvc.perform(post("/api/registrar/close-registration").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leveled", hasItem("ART-120")))
                .andExpect(jsonPath("$.cancelled", hasItem("CS-401")))
                .andExpect(jsonPath("$.cancelled", not(hasItem("ART-120"))))
                .andExpect(jsonPath("$.billed").value(1));

        mockMvc.perform(get("/api/schedules/me").header("Authorization", "Bearer " + student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("submitted"))
                .andExpect(jsonPath("$.items[?(@.offeringId=='ART-120')].type").value("primary"))
                .andExpect(jsonPath("$.items[?(@.offeringId=='ART-120')].status").value("enrolled"))
                .andExpect(jsonPath("$.items[?(@.offeringId=='CS-401')]").isEmpty());

        assertThat(enrollmentRepository.existsByOfferingIdAndStudentId("ART-120", "S001")).isTrue();
        assertThat(courseOfferingRepository.findById("ART-120").orElseThrow().getSeatsTaken()).isEqualTo(3);
    }

    @Test
    void stillUnderMinAfterLevelingIsCancelled() throws Exception {
        String student = loginToken("student");
        mockMvc.perform(post("/api/schedules/me/submit")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemsJson(primary("CS-301"), alternate("PHYS-150"))))
                .andExpect(status().isOk());

        String admin = loginToken("admin");
        mockMvc.perform(post("/api/registrar/close-registration").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leveled.length()").value(0))
                .andExpect(jsonPath("$.cancelled", containsInAnyOrder("ART-120", "CS-401")))
                .andExpect(jsonPath("$.billed").value(1));

        mockMvc.perform(get("/api/schedules/me").header("Authorization", "Bearer " + student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.offeringId=='PHYS-150')].type").value("alternate"))
                .andExpect(jsonPath("$.items[?(@.offeringId=='PHYS-150')].status").value("selected"));
    }

    @Test
    void savedScheduleIsNotLeveled() throws Exception {
        String student = loginToken("student");
        mockMvc.perform(put("/api/schedules/me")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemsJson(primary("CS-301"), alternate("ART-120"))))
                .andExpect(status().isOk());

        String admin = loginToken("admin");
        mockMvc.perform(post("/api/registrar/close-registration").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leveled.length()").value(0))
                .andExpect(jsonPath("$.billed").value(0))
                .andExpect(jsonPath("$.cancelled", hasItem("ART-120")));

        StudentSchedule schedule = studentScheduleRepository.findByStudentIdAndTerm("S001", "2026 秋季").orElseThrow();
        assertThat(schedule.getStatus().name()).isEqualTo("saved");
        assertThat(scheduleItemRepository.findByScheduleId(schedule.getId()))
                .extracting(ScheduleItemEntity::getOfferingId)
                .containsExactly("CS-301");
        assertThat(scheduleItemRepository.findByScheduleId(schedule.getId()).get(0).getStatus())
                .isEqualTo(ItemStatus.selected);
        assertThat(scheduleItemRepository.findByScheduleId(schedule.getId()).get(0).getType())
                .isEqualTo(ItemType.primary);
    }

    @Test
    void alreadyClosedCannotCloseAgain() throws Exception {
        String admin = loginToken("admin");
        mockMvc.perform(post("/api/registrar/close-registration").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/registrar/close-registration").header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("注册已经关闭，不能重复执行"));
    }

    @Test
    void studentScheduleLockedAfterClose() throws Exception {
        String student = loginToken("student");
        mockMvc.perform(post("/api/schedules/me/submit")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemsJson(primary("CS-301"))))
                .andExpect(status().isOk());

        String admin = loginToken("admin");
        mockMvc.perform(post("/api/registrar/close-registration").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/schedules/me")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemsJson(primary("BUS-110"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("本学期注册已关闭，无法修改课表"));
        mockMvc.perform(post("/api/schedules/me/submit")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemsJson(primary("BUS-110"))))
                .andExpect(status().isConflict());

        String teacher = loginToken("teacher");
        mockMvc.perform(put("/api/professor/teaching")
                        .header("Authorization", "Bearer " + teacher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeringIds\":[\"CS-301\"]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void nonRegistrarCannotClose() throws Exception {
        String student = loginToken("student");
        mockMvc.perform(post("/api/registrar/close-registration").header("Authorization", "Bearer " + student))
                .andExpect(status().isForbidden());
    }

    private void restoreOffering(String id, String professorId, String professorName, int seatsTaken) {
        CourseOffering offering = courseOfferingRepository.findById(id).orElseThrow();
        offering.setProfessorId(professorId);
        offering.setProfessorName(professorName);
        offering.setSeatsTaken(seatsTaken);
        offering.setCancelled(false);
        offering.setOfferingClosed(false);
        courseOfferingRepository.save(offering);
    }

    private static String primary(String id) {
        return "{\"offeringId\":\"" + id + "\",\"type\":\"primary\",\"status\":\"selected\"}";
    }

    private static String alternate(String id) {
        return "{\"offeringId\":\"" + id + "\",\"type\":\"alternate\",\"status\":\"selected\"}";
    }

    private static String itemsJson(String... items) {
        return "{\"items\":[" + String.join(",", items) + "]}";
    }

    private String loginToken(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TestAuth.json(username)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("token").asText();
    }
}
