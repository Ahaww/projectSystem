package edu.wylie.crs.entity;

import edu.wylie.crs.domain.ScheduleStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "student_schedule",
        uniqueConstraints = @UniqueConstraint(name = "uk_schedule_student_term", columnNames = {"student_id", "term"})
)
public class StudentSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String studentId;

    @Column(nullable = false, length = 32)
    private String term;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ScheduleStatus status = ScheduleStatus.draft;

    private Instant submitTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getTerm() { return term; }
    public void setTerm(String term) { this.term = term; }
    public ScheduleStatus getStatus() { return status; }
    public void setStatus(ScheduleStatus status) { this.status = status; }
    public Instant getSubmitTime() { return submitTime; }
    public void setSubmitTime(Instant submitTime) { this.submitTime = submitTime; }
}
