package edu.wylie.crs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.wylie.crs.entity.CourseOffering;
import edu.wylie.crs.entity.TermConfig;
import edu.wylie.crs.integration.CourseCatalogClientStub;
import edu.wylie.crs.repository.CourseOfferingRepository;
import edu.wylie.crs.repository.TermConfigRepository;
import edu.wylie.crs.service.CatalogCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class Step2CatalogQueryTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TermConfigRepository termConfigRepository;

    @Autowired
    private CourseOfferingRepository courseOfferingRepository;

    @Autowired
    private CourseCatalogClientStub courseCatalogClientStub;

    @Autowired
    private CatalogCacheService catalogCacheService;

    @Test
    void studentCatalogHidesProfessorBeforeClose() throws Exception {
        String student = loginToken("student");
        MvcResult result = mockMvc.perform(get("/api/offerings").header("Authorization", "Bearer " + student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(13))
                .andExpect(jsonPath("$[?(@.id=='CS-301')].title").value("软件工程"))
                .andExpect(jsonPath("$[?(@.id=='MATH-214')].seatsTaken").value(10))
                .andExpect(jsonPath("$[?(@.id=='CS-401')].prerequisites[0]").value("CS-301"))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        for (JsonNode offering : body) {
            assertThat(offering.get("professor").asText()).isEmpty();
            assertThat(offering.has("cancelled")).isFalse();
        }
    }

    @Test
    void registrarOfferingsAlwaysIncludeProfessorNames() throws Exception {
        String admin = loginToken("admin");
        mockMvc.perform(get("/api/registrar/offerings").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(13))
                .andExpect(jsonPath("$[?(@.id=='CS-301')].professor").value("Morgan 博士"))
                .andExpect(jsonPath("$[?(@.id=='CS-401')].professor").value("Chen 博士"));
    }

    @Test
    void nonRegistrarCannotAccessAdminCatalog() throws Exception {
        String student = loginToken("student");
        mockMvc.perform(get("/api/registrar/offerings").header("Authorization", "Bearer " + student))
                .andExpect(status().isForbidden());
    }

    @Test
    void offeringsSupportKeywordAndDeptFilter() throws Exception {
        String student = loginToken("student");
        mockMvc.perform(get("/api/offerings")
                        .param("keyword", "数据库")
                        .header("Authorization", "Bearer " + student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("CS-401"));

        mockMvc.perform(get("/api/offerings")
                        .param("dept", "计算机科学")
                        .header("Authorization", "Bearer " + student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    void studentSeesProfessorNameAfterRegistrationClosed() throws Exception {
        TermConfig config = termConfigRepository.findById("2026 秋季").orElseThrow();
        config.setRegistrationClosed(true);
        termConfigRepository.save(config);
        try {
            String student = loginToken("student");
            mockMvc.perform(get("/api/offerings").header("Authorization", "Bearer " + student))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.id=='CS-301')].professor").value("Morgan 博士"));
        } finally {
            config.setRegistrationClosed(false);
            termConfigRepository.save(config);
        }
    }

    @Test
    void cancelledOfferingsCarryFlag() throws Exception {
        CourseOffering art = courseOfferingRepository.findById("ART-120").orElseThrow();
        art.setCancelled(true);
        courseOfferingRepository.save(art);
        try {
            String student = loginToken("student");
            String admin = loginToken("admin");
            mockMvc.perform(get("/api/offerings").header("Authorization", "Bearer " + student))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.id=='ART-120')].cancelled").value(true));
            mockMvc.perform(get("/api/registrar/offerings").header("Authorization", "Bearer " + admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.id=='ART-120')].cancelled").value(true));
        } finally {
            art.setCancelled(false);
            courseOfferingRepository.save(art);
        }
    }

    @Test
    void catalogUnavailableWithCacheStillServesOfferings() throws Exception {
        courseCatalogClientStub.setAvailable(false);
        try {
            String student = loginToken("student");
            mockMvc.perform(get("/api/offerings").header("Authorization", "Bearer " + student))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(13))
                    .andExpect(jsonPath("$[?(@.id=='CS-301')].title").value("软件工程"));
        } finally {
            courseCatalogClientStub.setAvailable(true);
        }
    }

    @Test
    void catalogUnavailableWithEmptyCacheIs503() throws Exception {
        List<CourseOffering> snapshot = courseOfferingRepository.findAll();
        courseCatalogClientStub.setAvailable(false);
        courseOfferingRepository.deleteAll();
        try {
            String student = loginToken("student");
            mockMvc.perform(get("/api/offerings").header("Authorization", "Bearer " + student))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.message").value("课程目录系统不可用"));
        } finally {
            courseCatalogClientStub.setAvailable(true);
            courseOfferingRepository.saveAll(snapshot);
            catalogCacheService.syncCurrentTerm();
        }
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
