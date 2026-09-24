package edu.wylie.crs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.wylie.crs.entity.CourseOffering;
import edu.wylie.crs.entity.TeachingAssignment;
import edu.wylie.crs.entity.TermConfig;
import edu.wylie.crs.repository.CourseOfferingRepository;
import edu.wylie.crs.repository.TeachingAssignmentRepository;
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
class Step4ProfessorTeachingTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TermConfigRepository termConfigRepository;

    @Autowired
    private CourseOfferingRepository courseOfferingRepository;

    @Autowired
    private TeachingAssignmentRepository teachingAssignmentRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void resetTeaching() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            teachingAssignmentRepository.deleteAll();
            CourseOffering cs301 = courseOfferingRepository.findById("CS-301").orElseThrow();
            cs301.setProfessorId("P001");
            cs301.setProfessorName("Morgan 博士");
            courseOfferingRepository.save(cs301);
            CourseOffering cs401 = courseOfferingRepository.findById("CS-401").orElseThrow();
            cs401.setProfessorId("P003");
            cs401.setProfessorName("Chen 博士");
            courseOfferingRepository.save(cs401);
            TermConfig config = termConfigRepository.findById("2026 秋季").orElseThrow();
            config.setRegistrationClosed(false);
            termConfigRepository.save(config);
        });
    }

    @Test
    void eligibleReturnsSameDeptOfferingsWithTeachingFlag() throws Exception {
        String teacher = loginToken("teacher");
        mockMvc.perform(get("/api/professor/eligible").header("Authorization", "Bearer " + teacher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[?(@.id=='CS-301')].teaching").value(false))
                .andExpect(jsonPath("$[?(@.id=='CS-401')].teaching").value(false))
                .andExpect(jsonPath("$[?(@.id=='MATH-214')]").isEmpty());
    }

    @Test
    void updateTeachingReplacesSelectionAndMarksTeaching() throws Exception {
        String teacher = loginToken("teacher");
        mockMvc.perform(put("/api/professor/teaching")
                        .header("Authorization", "Bearer " + teacher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeringIds\":[\"CS-301\"]}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/professor/eligible").header("Authorization", "Bearer " + teacher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='CS-301')].teaching").value(true))
                .andExpect(jsonPath("$[?(@.id=='CS-401')].teaching").value(false));

        assertThat(teachingAssignmentRepository.findByProfessorIdAndTerm("P001", "2026 秋季"))
                .extracting(TeachingAssignment::getOfferingId)
                .containsExactly("CS-301");
        assertThat(courseOfferingRepository.findById("CS-301").orElseThrow().getProfessorId())
                .isEqualTo("P001");
    }

    @Test
    void conflictingTimesCannotBeSavedTogether() throws Exception {
        String teacher = loginToken("teacher");
        mockMvc.perform(put("/api/professor/teaching")
                        .header("Authorization", "Bearer " + teacher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeringIds\":[\"CS-301\",\"CS-401\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("所选课程存在时间冲突"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].code").value("conflict"));

        assertThat(teachingAssignmentRepository.findByProfessorIdAndTerm("P001", "2026 秋季")).isEmpty();
    }

    @Test
    void canClearTeachingSelection() throws Exception {
        String teacher = loginToken("teacher");
        mockMvc.perform(put("/api/professor/teaching")
                        .header("Authorization", "Bearer " + teacher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeringIds\":[\"CS-301\"]}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/professor/teaching")
                        .header("Authorization", "Bearer " + teacher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeringIds\":[]}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/professor/eligible").header("Authorization", "Bearer " + teacher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='CS-301')].teaching").value(false));
        assertThat(courseOfferingRepository.findById("CS-301").orElseThrow().getProfessorId()).isNull();
    }

    @Test
    void cannotOverwriteAnotherProfessorsAssignment() throws Exception {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            TeachingAssignment other = new TeachingAssignment();
            other.setProfessorId("P003");
            other.setOfferingId("CS-301");
            other.setTerm("2026 秋季");
            teachingAssignmentRepository.save(other);
            CourseOffering cs301 = courseOfferingRepository.findById("CS-301").orElseThrow();
            cs301.setProfessorId("P003");
            cs301.setProfessorName("Chen 博士");
            courseOfferingRepository.save(cs301);
        });

        String teacher = loginToken("teacher");
        mockMvc.perform(put("/api/professor/teaching")
                        .header("Authorization", "Bearer " + teacher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeringIds\":[\"CS-301\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].offeringId").value("CS-301"));
    }

    @Test
    void closedRegistrationBlocksTeachingUpdate() throws Exception {
        TermConfig config = termConfigRepository.findById("2026 秋季").orElseThrow();
        config.setRegistrationClosed(true);
        termConfigRepository.save(config);

        String teacher = loginToken("teacher");
        mockMvc.perform(put("/api/professor/teaching")
                        .header("Authorization", "Bearer " + teacher)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeringIds\":[\"CS-301\"]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void nonProfessorCannotAccessTeachingApis() throws Exception {
        String student = loginToken("student");
        mockMvc.perform(get("/api/professor/eligible").header("Authorization", "Bearer " + student))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/professor/teaching")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeringIds\":[\"CS-301\"]}"))
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
