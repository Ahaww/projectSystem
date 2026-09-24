package edu.wylie.crs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.wylie.crs.entity.CourseOffering;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class Step3StudentScheduleTests {

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
    void resetStudentSchedule() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            studentScheduleRepository.findByStudentId("S001").forEach(schedule -> {
                scheduleItemRepository.deleteByScheduleId(schedule.getId());
                studentScheduleRepository.delete(schedule);
            });
            enrollmentRepository.findByStudentIdAndTerm("S001", "2026 秋季")
                    .forEach(enrollmentRepository::delete);
            restoreSeats("CS-301", 8);
            restoreSeats("MATH-214", 10);
            restoreSeats("BUS-110", 4);
            restoreSeats("ART-120", 2);
            TermConfig config = termConfigRepository.findById("2026 秋季").orElseThrow();
            config.setRegistrationClosed(false);
            termConfigRepository.save(config);
        });
    }

    @Test
    void getMineReturnsEmptyDraftWhenNoSchedule() throws Exception {
        String student = loginToken("student");
        mockMvc.perform(get("/api/schedules/me").header("Authorization", "Bearer " + student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("draft"))
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.submitTime").doesNotExist());
    }

    @Test
    void saveDoesNotOccupySeats() throws Exception {
        String student = loginToken("student");
        int before = seatsTaken("CS-301");
        mockMvc.perform(put("/api/schedules/me")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemsJson(primary("CS-301"), alternate("ART-120"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("saved"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[?(@.offeringId=='CS-301')].status").value("selected"))
                .andExpect(jsonPath("$.items[?(@.offeringId=='ART-120')].priority").value(1))
                .andExpect(jsonPath("$.submitTime").doesNotExist());
        assertThat(seatsTaken("CS-301")).isEqualTo(before);
        assertThat(enrollmentRepository.existsByOfferingIdAndStudentId("CS-301", "S001")).isFalse();
    }

    @Test
    void saveRejectsDuplicateAndOverLimitAndConflict() throws Exception {
        String student = loginToken("student");
        mockMvc.perform(put("/api/schedules/me")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemsJson(primary("CS-301"), primary("CS-301"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("conflict"));

        mockMvc.perform(put("/api/schedules/me")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemsJson(
                                primary("CS-301"),
                                primary("BUS-110"),
                                primary("HIST-101"),
                                primary("ART-120"),
                                primary("MATH-214"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.message=='主选课程不能超过 4 门')]").exists());

        mockMvc.perform(put("/api/schedules/me")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemsJson(primary("CS-301"), primary("CS-401"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("conflict"))
                .andExpect(jsonPath("$.errors[0].offeringId").value("CS-401"));
    }

    @Test
    void submitOccupiesPrimarySeatsAndKeepsAlternateSelected() throws Exception {
        String student = loginToken("student");
        int csBefore = seatsTaken("CS-301");
        int artBefore = seatsTaken("ART-120");
        mockMvc.perform(post("/api/schedules/me/submit")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemsJson(primary("CS-301"), alternate("ART-120"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("submitted"))
                .andExpect(jsonPath("$.items[?(@.offeringId=='CS-301')].status").value("enrolled"))
                .andExpect(jsonPath("$.items[?(@.offeringId=='ART-120')].status").value("selected"))
                .andExpect(jsonPath("$.submitTime").isNotEmpty());
        assertThat(seatsTaken("CS-301")).isEqualTo(csBefore + 1);
        assertThat(seatsTaken("ART-120")).isEqualTo(artBefore);
        assertThat(enrollmentRepository.existsByOfferingIdAndStudentId("CS-301", "S001")).isTrue();
        assertThat(enrollmentRepository.existsByOfferingIdAndStudentId("ART-120", "S001")).isFalse();
    }

    @Test
    void submitFullAndMissingPrerequisiteReturn422() throws Exception {
        String student = loginToken("student");
        int mathBefore = seatsTaken("MATH-214");
        mockMvc.perform(post("/api/schedules/me/submit")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemsJson(primary("MATH-214"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("full"))
                .andExpect(jsonPath("$.errors[0].offeringId").value("MATH-214"));
        assertThat(seatsTaken("MATH-214")).isEqualTo(mathBefore);

        mockMvc.perform(post("/api/schedules/me/submit")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemsJson(primary("CS-401"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("prerequisite"))
                .andExpect(jsonPath("$.errors[0].offeringId").value("CS-401"));
    }

    @Test
    void resubmitSamePrimaryDoesNotDoubleCount() throws Exception {
        String student = loginToken("student");
        String body = itemsJson(primary("BUS-110"));
        mockMvc.perform(post("/api/schedules/me/submit")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        int afterFirst = seatsTaken("BUS-110");
        mockMvc.perform(post("/api/schedules/me/submit")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        assertThat(seatsTaken("BUS-110")).isEqualTo(afterFirst);
        assertThat(enrollmentRepository.findByStudentIdAndTerm("S001", "2026 秋季")).hasSize(1);
    }

    @Test
    void saveAfterSubmitReleasesSeats() throws Exception {
        String student = loginToken("student");
        int before = seatsTaken("CS-301");
        mockMvc.perform(post("/api/schedules/me/submit")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemsJson(primary("CS-301"))))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/schedules/me")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemsJson(primary("CS-301"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("saved"))
                .andExpect(jsonPath("$.items[0].status").value("selected"));
        assertThat(seatsTaken("CS-301")).isEqualTo(before);
        assertThat(enrollmentRepository.existsByOfferingIdAndStudentId("CS-301", "S001")).isFalse();
    }

    @Test
    void deleteReleasesEnrolledSeats() throws Exception {
        String student = loginToken("student");
        int before = seatsTaken("CS-301");
        mockMvc.perform(post("/api/schedules/me/submit")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemsJson(primary("CS-301"))))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/schedules/me").header("Authorization", "Bearer " + student))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/schedules/me").header("Authorization", "Bearer " + student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("draft"))
                .andExpect(jsonPath("$.items.length()").value(0));
        assertThat(seatsTaken("CS-301")).isEqualTo(before);
        assertThat(enrollmentRepository.existsByOfferingIdAndStudentId("CS-301", "S001")).isFalse();
    }

    @Test
    void writesForbiddenWhenRegistrationClosed() throws Exception {
        TermConfig config = termConfigRepository.findById("2026 秋季").orElseThrow();
        config.setRegistrationClosed(true);
        termConfigRepository.save(config);
        String student = loginToken("student");
        mockMvc.perform(put("/api/schedules/me")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemsJson(primary("CS-301"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("本学期注册已关闭，无法修改课表"));
        mockMvc.perform(post("/api/schedules/me/submit")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemsJson(primary("CS-301"))))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/schedules/me").header("Authorization", "Bearer " + student))
                .andExpect(status().isConflict());
    }

    @Test
    void nonStudentCannotAccessSchedule() throws Exception {
        String teacher = loginToken("teacher");
        mockMvc.perform(get("/api/schedules/me").header("Authorization", "Bearer " + teacher))
                .andExpect(status().isForbidden());
    }

    private int seatsTaken(String offeringId) {
        return courseOfferingRepository.findById(offeringId).orElseThrow().getSeatsTaken();
    }

    private void restoreSeats(String offeringId, int seatsTaken) {
        CourseOffering offering = courseOfferingRepository.findById(offeringId).orElseThrow();
        offering.setSeatsTaken(seatsTaken);
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
