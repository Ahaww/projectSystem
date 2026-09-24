package edu.wylie.crs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 关闭注册后的计费投递队列。领域关闭已提交后，失败项按间隔重试，不回滚注册关闭状态。
 */
@Entity
@Table(name = "billing_retry_job")
public class BillingRetryJob {

    public static final String PENDING = "PENDING";
    public static final String SENT = "SENT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String studentId;

    @Column(nullable = false, length = 32)
    private String term;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String snapshotJson;

    @Column(nullable = false, length = 16)
    private String status;

    private int attempts;

    private Instant nextRetryAt;
    private Instant createdAt;
    private Instant sentAt;

    @Column(length = 512)
    private String lastError;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getTerm() { return term; }
    public void setTerm(String term) { this.term = term; }
    public String getSnapshotJson() { return snapshotJson; }
    public void setSnapshotJson(String snapshotJson) { this.snapshotJson = snapshotJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public Instant getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(Instant nextRetryAt) { this.nextRetryAt = nextRetryAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}
