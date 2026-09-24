package edu.wylie.crs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.wylie.crs.entity.BillingRetryJob;
import edu.wylie.crs.entity.CourseOffering;
import edu.wylie.crs.entity.TermConfig;
import edu.wylie.crs.integration.BillingSystemClientStub;
import edu.wylie.crs.integration.CourseCatalogClientStub;
import edu.wylie.crs.repository.BillingRetryJobRepository;
import edu.wylie.crs.repository.CourseOfferingRepository;
import edu.wylie.crs.repository.EnrollmentRepository;
import edu.wylie.crs.repository.ScheduleItemRepository;
import edu.wylie.crs.repository.StudentScheduleRepository;
import edu.wylie.crs.repository.TermConfigRepository;
import edu.wylie.crs.service.BillingRetryService;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class Step7ResilienceTests {

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
    private BillingRetryJobRepository billingRetryJobRepository;

    @Autowired
    private BillingRetryService billingRetryService;

    @Autowired
    private BillingSystemClientStub billingSystemClientStub;

    @Autowired
    private CourseCatalogClientStub courseCatalogClientStub;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void reset() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (String studentId : List.of("S001", "S002")) {
                studentScheduleRepository.findByStudentId(studentId).forEach(schedule -> {
                    scheduleItemRepository.deleteByScheduleId(schedule.getId());
                    studentScheduleRepository.delete(schedule);
                });
                enrollmentRepository.findByStudentIdAndTerm(studentId, "2026 秋季")
                        .forEach(enrollmentRepository::delete);
            }
            restoreSeats("HIST-101", 5);
            restoreSeats("CS-301", 8);
            restoreSeats("ART-120", 2);
            billingRetryJobRepository.deleteAll();
            TermConfig config = termConfigRepository.findById("2026 秋季").orElseThrow();
            config.setRegistrationClosed(false);
            termConfigRepository.save(config);
        });
        billingSystemClientStub.setAvailable(true);
        billingSystemClientStub.setFailNext(false);
        billingSystemClientStub.resetSendCount();
        courseCatalogClientStub.setAvailable(true);
    }

    @Test
    void lastSeatOnlyOneSubmitSucceeds() throws Exception {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> restoreSeats("HIST-101", 9));

        String studentA = loginToken("student");
        String studentB = loginToken("s002");
        String body = itemsJson(primary("HIST-101"));

        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger full = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> futures = new ArrayList<>();
            futures.add(pool.submit(submitOnce(studentA, body, start, success, full)));
            futures.add(pool.submit(submitOnce(studentB, body, start, success, full)));
            start.countDown();
            for (Future<?> future : futures) {
                future.get(15, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(success.get()).isEqualTo(1);
        assertThat(full.get()).isEqualTo(1);
        assertThat(courseOfferingRepository.findById("HIST-101").orElseThrow().getSeatsTaken()).isEqualTo(10);
        long enrolled = enrollmentRepository.findByOfferingId("HIST-101").stream()
                .filter(e -> "2026 秋季".equals(e.getTerm()))
                .count();
        assertThat(enrolled).isEqualTo(1);
    }

    @Test
    void professorEligibleUsesCacheWhenCatalogDownAnd503WhenEmpty() throws Exception {
        courseCatalogClientStub.setAvailable(false);
        String teacher = loginToken("teacher");
        mockMvc.perform(get("/api/professor/eligible").header("Authorization", "Bearer " + teacher))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='CS-301')]").isNotEmpty());

        List<CourseOffering> current = courseOfferingRepository.findByTerm("2026 秋季");
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                courseOfferingRepository.deleteAll(current));
        try {
            mockMvc.perform(get("/api/professor/eligible").header("Authorization", "Bearer " + teacher))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.message").value("课程目录系统不可用"));
        } finally {
            new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    courseOfferingRepository.saveAll(current));
            courseCatalogClientStub.setAvailable(true);
        }
    }

    @Test
    void closeSucceedsWhenBillingFailsThenRetrySendsWithoutReopening() throws Exception {
        String student = loginToken("student");
        mockMvc.perform(post("/api/schedules/me/submit")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemsJson(primary("CS-301"))))
                .andExpect(status().isOk());

        billingSystemClientStub.setAvailable(false);
        billingSystemClientStub.resetSendCount();

        String admin = loginToken("admin");
        mockMvc.perform(post("/api/registrar/close-registration").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billed").value(1));

        assertThat(termConfigRepository.findById("2026 秋季").orElseThrow().isRegistrationClosed()).isTrue();
        assertThat(billingSystemClientStub.getSendCount()).isZero();
        assertThat(billingRetryJobRepository.countByTermAndStatus("2026 秋季", BillingRetryJob.PENDING)).isEqualTo(1);

        mockMvc.perform(post("/api/registrar/close-registration").header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("注册已经关闭，不能重复执行"));

        billingSystemClientStub.setAvailable(true);
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                billingRetryJobRepository.findByTermOrderByIdAsc("2026 秋季").forEach(job -> {
                    job.setNextRetryAt(java.time.Instant.EPOCH);
                    billingRetryJobRepository.save(job);
                }));
        assertThat(billingRetryService.retryDue()).isEqualTo(1);
        assertThat(billingSystemClientStub.getSendCount()).isEqualTo(1);
        assertThat(billingRetryJobRepository.countByTermAndStatus("2026 秋季", BillingRetryJob.SENT)).isEqualTo(1);
        assertThat(termConfigRepository.findById("2026 秋季").orElseThrow().isRegistrationClosed()).isTrue();
    }

    @Test
    void cannotDeleteEnrolledStudentOrAssignedProfessor() throws Exception {
        String student = loginToken("student");
        mockMvc.perform(post("/api/schedules/me/submit")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemsJson(primary("CS-301"))))
                .andExpect(status().isOk());

        String admin = loginToken("admin");
        mockMvc.perform(delete("/api/registrar/students/S001").header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("该学生本学期仍有在册选课，不能删除"));
        mockMvc.perform(delete("/api/registrar/professors/P001").header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("该教授本学期仍有任教安排，不能删除"));
    }

    @Test
    void failingGradeDoesNotSatisfyPrerequisiteButOpenCoursesSubmit() throws Exception {
        String student = loginToken("student");
        mockMvc.perform(post("/api/schedules/me/submit")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemsJson(primary("PHYS-150"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("prerequisite"))
                .andExpect(jsonPath("$.errors[0].offeringId").value("PHYS-150"));

        mockMvc.perform(post("/api/schedules/me/submit")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemsJson(primary("CS-301"), primary("BUS-110"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("submitted"));
    }

    @Test
    void currentTermOfferingsHaveCreditsForTranscript() {
        assertThat(courseOfferingRepository.findById("ART-120").orElseThrow().getCredits()).isEqualTo(2);
        assertThat(courseOfferingRepository.findById("PHYS-150").orElseThrow().getCredits()).isEqualTo(4);
        assertThat(courseOfferingRepository.findById("MATH-110").orElseThrow().getCredits()).isEqualTo(4);
    }

    @Test
    void permissionMatrixMatchesRoles() throws Exception {
        String student = loginToken("student");
        String teacher = loginToken("teacher");
        String admin = loginToken("admin");

        mockMvc.perform(post("/api/registrar/close-registration").header("Authorization", "Bearer " + student))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/registrar/students").header("Authorization", "Bearer " + teacher))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/professor/offerings/CS-210/grades")
                        .header("Authorization", "Bearer " + student)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grades\":[]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/report-card/me").header("Authorization", "Bearer " + teacher))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/report-card/me").header("Authorization", "Bearer " + admin))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/schedules/me").header("Authorization", "Bearer " + admin))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/professor/teaching")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeringIds\":[\"CS-301\"]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/professor/offerings").header("Authorization", "Bearer " + admin))
                .andExpect(status().isForbidden());
    }

    private Runnable submitOnce(
            String token,
            String body,
            CountDownLatch start,
            AtomicInteger success,
            AtomicInteger full
    ) {
        return () -> {
            try {
                start.await(10, TimeUnit.SECONDS);
                MvcResult result = mockMvc.perform(post("/api/schedules/me/submit")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andReturn();
                int status = result.getResponse().getStatus();
                if (status == 200) {
                    success.incrementAndGet();
                } else if (status == 422) {
                    full.incrementAndGet();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    private void restoreSeats(String offeringId, int seatsTaken) {
        CourseOffering offering = courseOfferingRepository.findById(offeringId).orElseThrow();
        offering.setSeatsTaken(seatsTaken);
        offering.setCancelled(false);
        offering.setOfferingClosed(false);
        courseOfferingRepository.save(offering);
    }

    private static String primary(String id) {
        return "{\"offeringId\":\"" + id + "\",\"type\":\"primary\",\"status\":\"selected\"}";
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
