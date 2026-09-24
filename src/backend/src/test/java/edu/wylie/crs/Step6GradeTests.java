package edu.wylie.crs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.wylie.crs.entity.Enrollment;
import edu.wylie.crs.entity.TermConfig;
import edu.wylie.crs.repository.EnrollmentRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class Step6GradeTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private TermConfigRepository termConfigRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void resetGrades() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            enrollmentRepository.findByOfferingIdAndStudentId("CS-210", "S002")
                    .ifPresent(e -> {
                        e.setGrade(null);
                        enrollmentRepository.save(e);
                    });
            TermConfig config = termConfigRepository.findById("2026 秋季").orElseThrow();
            config.setRegistrationClosed(false);
            termConfigRepository.save(config);
        });
    }

    @Test
    void studentReportCardShowsOwnMultiTermGrades() throws Exception {
        String student = loginToken("student");
        mockMvc.perform(get("/api/report-card/me").header("Authorization", "Bearer " + student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semesters.length()").value(2))
                .andExpect(jsonPath("$.semesters[0].term").value("2025 秋季"))
                .andExpect(jsonPath("$.semesters[0].items[?(@.code=='CS-210')].title").value("程序设计"))
                .andExpect(jsonPath("$.semesters[0].items[?(@.code=='CS-210')].credits").value(3))
                .andExpect(jsonPath("$.semesters[0].items[?(@.code=='MATH-110')].credits").value(4))
                .andExpect(jsonPath("$.semesters[0].items[?(@.code=='ENG-105')].credits").value(2))
                .andExpect(jsonPath("$.semesters[0].items[?(@.code=='CS-210')].grade").value("A"))
                .andExpect(jsonPath("$.semesters[1].term").value("2025 春季"))
                .andExpect(jsonPath("$.semesters[1].items[?(@.code=='PHYS-101')].grade").value("B"));
    }

    @Test
    void professorCannotReadStudentReportCard() throws Exception {
        String teacher = loginToken("teacher");
        mockMvc.perform(get("/api/report-card/me").header("Authorization", "Bearer " + teacher))
                .andExpect(status().isForbidden());
    }

    @Test
    void taughtOfferingsArePreviousClosedTermOnly() throws Exception {
        String teacher = loginToken("teacher");
        mockMvc.perform(get("/api/professor/offerings").header("Authorization", "Bearer " + teacher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id=='CS-210')].students").value(5))
                .andExpect(jsonPath("$[?(@.id=='MATH-110')].term").value("2025 秋季"))
                .andExpect(jsonPath("$[?(@.id=='CS-301')]").isEmpty());
    }

    @Test
    void historicalRosterIsVisibleWhileCurrentRegistrationOpen() throws Exception {
        String teacher = loginToken("teacher");
        mockMvc.perform(get("/api/professor/offerings/CS-210/roster")
                        .header("Authorization", "Bearer " + teacher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].studentId").value("S001"))
                .andExpect(jsonPath("$[0].name").value("Jordan Davis"))
                .andExpect(jsonPath("$[0].grade").value("A"));
    }

    @Test
    void currentTermRosterIs409UntilRegistrationCloses() throws Exception {
        String teacher = loginToken("teacher");
        mockMvc.perform(get("/api/professor/offerings/CS-301/roster")
                        .header("Authorization", "Bearer " + teacher))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("注册尚未关闭，暂不可查看选课名册"));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            TermConfig config = termConfigRepository.findById("2026 秋季").orElseThrow();
            config.setRegistrationClosed(true);
            termConfigRepository.save(config);
        });

        mockMvc.perform(get("/api/professor/offerings/CS-301/roster")
                        .header("Authorization", "Bearer " + teacher))
                .andExpect(status().isOk());
    }

    @Test
    void cannotGradeSomeoneElsesSection() throws Exception {
        String teacher = loginToken("teacher");
        mockMvc.perform(get("/api/professor/offerings/HIST-101/roster")
                        .header("Authorization", "Bearer " + teacher))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/professor/offerings/HIST-101/grades")
                        .header("Authorization", "Bearer " + teacher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grades\":[{\"studentId\":\"S001\",\"grade\":\"A\"}]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingOfferingIs404() throws Exception {
        String teacher = loginToken("teacher");
        mockMvc.perform(get("/api/professor/offerings/NO-SUCH/roster")
                        .header("Authorization", "Bearer " + teacher))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidGradeIsRejected() throws Exception {
        String teacher = loginToken("teacher");
        mockMvc.perform(put("/api/professor/offerings/CS-210/grades")
                        .header("Authorization", "Bearer " + teacher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grades\":[{\"studentId\":\"S002\",\"grade\":\"E\"}]}"))
                .andExpect(status().isBadRequest());
        assertThat(enrollmentRepository.findByOfferingIdAndStudentId("CS-210", "S002").orElseThrow().getGrade())
                .isNull();
    }

    @Test
    void partialSaveAndUpdateExistingGrade() throws Exception {
        String teacher = loginToken("teacher");
        mockMvc.perform(put("/api/professor/offerings/CS-210/grades")
                        .header("Authorization", "Bearer " + teacher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grades\":[{\"studentId\":\"S002\",\"grade\":\"A\"}]}"))
                .andExpect(status().isOk());

        Enrollment first = enrollmentRepository.findByOfferingIdAndStudentId("CS-210", "S002").orElseThrow();
        assertThat(first.getGrade()).isEqualTo("A");
        assertThat(enrollmentRepository.findByOfferingIdAndStudentId("CS-210", "S004").orElseThrow().getGrade())
                .isNull();

        mockMvc.perform(put("/api/professor/offerings/CS-210/grades")
                        .header("Authorization", "Bearer " + teacher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grades\":[{\"studentId\":\"S002\",\"grade\":\"I\"}]}"))
                .andExpect(status().isOk());
        assertThat(enrollmentRepository.findByOfferingIdAndStudentId("CS-210", "S002").orElseThrow().getGrade())
                .isEqualTo("I");
    }

    @Test
    void studentCannotSubmitGrades() throws Exception {
        String student = loginToken("student");
        mockMvc.perform(get("/api/professor/offerings").header("Authorization", "Bearer " + student))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/professor/offerings/CS-210/grades")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grades\":[{\"studentId\":\"S001\",\"grade\":\"A\"}]}"))
                .andExpect(status().isForbidden());
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
