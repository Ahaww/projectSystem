package edu.wylie.crs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "enrollment",
        uniqueConstraints = @UniqueConstraint(name = "uk_enrollment_offering_student", columnNames = {"offering_id", "student_id"})
)
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String offeringId;

    @Column(nullable = false, length = 32)
    private String studentId;

    @Column(length = 32)
    private String term;

    /** A/B/C/D/F/I，未录入则为空 */
    @Column(length = 4)
    private String grade;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOfferingId() { return offeringId; }
    public void setOfferingId(String offeringId) { this.offeringId = offeringId; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getTerm() { return term; }
    public void setTerm(String term) { this.term = term; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
}
