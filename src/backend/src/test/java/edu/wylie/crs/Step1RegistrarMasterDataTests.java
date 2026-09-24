package edu.wylie.crs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.wylie.crs.entity.Enrollment;
import edu.wylie.crs.repository.EnrollmentRepository;
import edu.wylie.crs.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class Step1RegistrarMasterDataTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Test
    void studentAndProfessorCrudRoundTrip() throws Exception {
        String admin = loginToken("admin");

        mockMvc.perform(get("/api/registrar/students").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("S001"))
                .andExpect(jsonPath("$[0].name").value("Jordan Davis"));

        MvcResult createdStudent = mockMvc.perform(post("/api/registrar/students")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新学生\",\"dob\":\"2006-01-01\",\"ssn\":\"110101200601010099\",\"status\":\"在读\",\"graduationDate\":\"2029-06-30\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("新学生"))
                .andReturn();
        String studentId = objectMapper.readTree(createdStudent.getResponse().getContentAsString()).get("id").asText();
        assertThat(studentId).startsWith("S");
        assertThat(userAccountRepository.findByUsername(studentId)).isPresent();

        mockMvc.perform(put("/api/registrar/students/" + studentId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新学生改名\",\"dob\":\"2006-01-01\",\"ssn\":\"110101200601010099\",\"status\":\"休学\",\"graduationDate\":\"2029-06-30\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/registrar/students").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + studentId + "')].name").value("新学生改名"));

        mockMvc.perform(delete("/api/registrar/students/" + studentId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        assertThat(userAccountRepository.findById(studentId)).isEmpty();

        mockMvc.perform(get("/api/registrar/professors").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("P001"));

        MvcResult createdProfessor = mockMvc.perform(post("/api/registrar/professors")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新教授\",\"dob\":\"1970-02-02\",\"ssn\":\"110101197002020011\",\"status\":\"在职\",\"dept\":\"历史\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("新教授"))
                .andReturn();
        String professorId = objectMapper.readTree(createdProfessor.getResponse().getContentAsString()).get("id").asText();
        assertThat(professorId).startsWith("P");

        mockMvc.perform(put("/api/registrar/professors/" + professorId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新教授改名\",\"dob\":\"1970-02-02\",\"ssn\":\"110101197002020011\",\"status\":\"休假\",\"dept\":\"历史\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/registrar/professors/" + professorId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
    }

    @Test
    void missingStudentOrProfessorIs404() throws Exception {
        String admin = loginToken("admin");
        mockMvc.perform(put("/api/registrar/students/S999")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"不存在\",\"dob\":\"2000-01-01\",\"ssn\":\"x\",\"status\":\"在读\",\"graduationDate\":\"2028-06-30\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/registrar/students/S999")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/registrar/professors/P999")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"不存在\",\"dob\":\"1970-01-01\",\"ssn\":\"x\",\"status\":\"在职\",\"dept\":\"数学\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/registrar/professors/P999")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonRegistrarGets403() throws Exception {
        String student = loginToken("student");
        String teacher = loginToken("teacher");
        mockMvc.perform(get("/api/registrar/students").header("Authorization", "Bearer " + student))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/registrar/professors").header("Authorization", "Bearer " + teacher))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/registrar/students")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"越权\",\"dob\":\"2006-01-01\",\"ssn\":\"x\",\"status\":\"在读\",\"graduationDate\":\"2029-06-30\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotDeleteStudentWithCurrentTermEnrollment() throws Exception {
        String admin = loginToken("admin");
        MvcResult created = mockMvc.perform(post("/api/registrar/students")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"在册生\",\"dob\":\"2006-01-01\",\"ssn\":\"x\",\"status\":\"在读\",\"graduationDate\":\"2029-06-30\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String studentId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        Enrollment enrollment = new Enrollment();
        enrollment.setStudentId(studentId);
        enrollment.setOfferingId("CS-301");
        enrollment.setTerm("2026 秋季");
        enrollmentRepository.save(enrollment);

        mockMvc.perform(delete("/api/registrar/students/" + studentId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict());

        enrollmentRepository.deleteAll(enrollmentRepository.findByStudentId(studentId));
        mockMvc.perform(delete("/api/registrar/students/" + studentId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
    }

    @Test
    void cannotDeleteProfessorAssignedThisTerm() throws Exception {
        String admin = loginToken("admin");
        mockMvc.perform(delete("/api/registrar/professors/P001")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict());
    }

    @Test
    void deletedIdIsNotReused() throws Exception {
        String admin = loginToken("admin");
        String firstId = createStudent(admin, "甲");
        mockMvc.perform(delete("/api/registrar/students/" + firstId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        String secondId = createStudent(admin, "乙");
        assertThat(secondId).isNotEqualTo(firstId);
        mockMvc.perform(delete("/api/registrar/students/" + secondId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
    }

    private String createStudent(String admin, String name) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/registrar/students")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"dob\":\"2006-01-01\",\"ssn\":\"x\",\"status\":\"在读\",\"graduationDate\":\"2029-06-30\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
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
