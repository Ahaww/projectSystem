package edu.wylie.crs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "professor")
public class Professor {

    @Id
    @Column(length = 32)
    private String id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 16)
    private String dob;

    @Column(length = 32)
    private String ssn;

    @Column(length = 16)
    private String status;

    @Column(length = 64)
    private String dept;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDob() { return dob; }
    public void setDob(String dob) { this.dob = dob; }
    public String getSsn() { return ssn; }
    public void setSsn(String ssn) { this.ssn = ssn; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDept() { return dept; }
    public void setDept(String dept) { this.dept = dept; }
}
