package edu.wylie.crs.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "teaching_assignment")
public class TeachingAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String professorId;

    @Column(nullable = false, length = 32)
    private String offeringId;

    @Column(nullable = false, length = 32)
    private String term;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProfessorId() { return professorId; }
    public void setProfessorId(String professorId) { this.professorId = professorId; }
    public String getOfferingId() { return offeringId; }
    public void setOfferingId(String offeringId) { this.offeringId = offeringId; }
    public String getTerm() { return term; }
    public void setTerm(String term) { this.term = term; }
}
