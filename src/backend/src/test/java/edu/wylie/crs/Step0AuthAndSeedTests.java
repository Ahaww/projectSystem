package edu.wylie.crs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.wylie.crs.repository.CourseOfferingRepository;
import edu.wylie.crs.repository.TermConfigRepository;
import edu.wylie.crs.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class Step0AuthAndSeedTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private TermConfigRepository termConfigRepository;

    @Autowired
    private CourseOfferingRepository courseOfferingRepository;

    @Test
    void seedCreatesUsersTermAndOfferings() {
        assertThat(userAccountRepository.findByUsername("S001")).isPresent();
        assertThat(userAccountRepository.findByUsername("S005")).isPresent();
        assertThat(userAccountRepository.findByUsername("P001")).isPresent();
        assertThat(userAccountRepository.findByUsername("P007")).isPresent();
        assertThat(userAccountRepository.findByUsername("R001")).isPresent();
        assertThat(termConfigRepository.findById("2026 秋季")).isPresent()
                .get()
                .satisfies(t -> assertThat(t.isRegistrationClosed()).isFalse());
        assertThat(courseOfferingRepository.findByTerm("2026 秋季")).hasSize(13);
        assertThat(courseOfferingRepository.findById("MATH-214")).isPresent()
                .get()
                .satisfies(o -> assertThat(o.getSeatsTaken()).isEqualTo(10));
    }

    @Test
    void idPasswordLoginsSucceed() throws Exception {
        loginOk("S001", "STUDENT", "S001", "Jordan Davis");
        loginOk("S002", "STUDENT", "S002", "李四");
        loginOk("P001", "PROFESSOR", "P001", "Alex Morgan");
        loginOk("P003", "PROFESSOR", "P003", "Chen 博士");
        loginOk("R001", "REGISTRAR", "R001", "王丽");
    }

    @Test
    void wrongPasswordIs401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"S001\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    void missingTokenOnBusinessApiIs401() throws Exception {
        mockMvc.perform(get("/api/registration/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registrationStatusRequiresLoginAndReturnsCurrentTerm() throws Exception {
        String token = loginToken("student");
        mockMvc.perform(get("/api/registration/status")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closed").value(false))
                .andExpect(jsonPath("$.term").value("2026 秋季"));
    }

    private void loginOk(String username, String role, String id, String name) throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TestAuth.json(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.id").value(id))
                .andExpect(jsonPath("$.user.name").value(name))
                .andExpect(jsonPath("$.user.role").value(role));
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
