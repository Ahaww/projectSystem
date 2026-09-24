package edu.wylie.crs.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.wylie.crs.entity.BillingRetryJob;
import edu.wylie.crs.integration.BillingSystemClient;
import edu.wylie.crs.repository.BillingRetryJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 计费投递与重试。关闭注册事务提交后尝试发送；失败保留 PENDING，不回滚已关闭状态。
 */
@Service
public class BillingRetryService {

    private static final Logger log = LoggerFactory.getLogger(BillingRetryService.class);
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final BillingRetryJobRepository billingRetryJobRepository;
    private final BillingSystemClient billingSystemClient;
    private final ObjectMapper objectMapper;
    private final Duration retryInterval;

    public BillingRetryService(
            BillingRetryJobRepository billingRetryJobRepository,
            BillingSystemClient billingSystemClient,
            ObjectMapper objectMapper,
            @Value("${crs.billing.retry-interval-ms:15000}") long retryIntervalMs
    ) {
        this.billingRetryJobRepository = billingRetryJobRepository;
        this.billingSystemClient = billingSystemClient;
        this.objectMapper = objectMapper;
        this.retryInterval = Duration.ofMillis(retryIntervalMs);
    }

    /** 与关闭注册同一事务写入待投递队列。 */
    @Transactional
    public int enqueue(List<Map<String, Object>> snapshots) {
        Instant now = Instant.now();
        int count = 0;
        for (Map<String, Object> snapshot : snapshots) {
            BillingRetryJob job = new BillingRetryJob();
            job.setStudentId(String.valueOf(snapshot.get("studentId")));
            job.setTerm(String.valueOf(snapshot.get("term")));
            job.setSnapshotJson(toJson(snapshot));
            job.setStatus(BillingRetryJob.PENDING);
            job.setAttempts(0);
            job.setCreatedAt(now);
            job.setNextRetryAt(now);
            billingRetryJobRepository.save(job);
            count++;
        }
        return count;
    }

    /** 关闭事务提交后再投递，避免计费失败回滚领域状态。 */
    public void dispatchAfterCommit() {
        Runnable send = this::retryDue;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    @Scheduled(fixedDelayString = "${crs.billing.retry-interval-ms:15000}")
    @Transactional
    public int retryDue() {
        List<BillingRetryJob> due = billingRetryJobRepository
                .findByStatusAndNextRetryAtLessThanEqualOrderByIdAsc(BillingRetryJob.PENDING, Instant.now());
        int sent = 0;
        for (BillingRetryJob job : due) {
            if (trySend(job)) {
                sent++;
            }
        }
        return sent;
    }

    private boolean trySend(BillingRetryJob job) {
        job.setAttempts(job.getAttempts() + 1);
        if (!billingSystemClient.isAvailable()) {
            defer(job, "计费系统不可用");
            log.warn("计费系统不可用，关闭注册已成功，待重试：studentId={} term={} attempts={}",
                    job.getStudentId(), job.getTerm(), job.getAttempts());
            return false;
        }
        try {
            billingSystemClient.sendBilling(job.getStudentId(), job.getTerm(), parseSnapshot(job.getSnapshotJson()));
            job.setStatus(BillingRetryJob.SENT);
            job.setSentAt(Instant.now());
            job.setLastError(null);
            billingRetryJobRepository.save(job);
            return true;
        } catch (RuntimeException ex) {
            defer(job, ex.getMessage());
            log.warn("计费投递失败，关闭注册已成功，待重试：studentId={} term={}", job.getStudentId(), job.getTerm(), ex);
            return false;
        }
    }

    private void defer(BillingRetryJob job, String error) {
        job.setStatus(BillingRetryJob.PENDING);
        job.setLastError(truncate(error));
        job.setNextRetryAt(Instant.now().plus(retryInterval));
        billingRetryJobRepository.save(job);
    }

    private Map<String, Object> parseSnapshot(String json) {
        try {
            return objectMapper.readValue(json, MAP);
        } catch (JsonProcessingException e) {
            return Map.of("raw", json == null ? "" : json);
        }
    }

    private String toJson(Map<String, Object> snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化计费快照失败", e);
        }
    }

    private static String truncate(String error) {
        if (error == null) {
            return "unknown";
        }
        return error.length() <= 512 ? error : error.substring(0, 512);
    }
}
