package edu.wylie.crs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "term_config")
public class TermConfig {

    @Id
    @Column(length = 32)
    private String term;

    private boolean registrationClosed;

    public String getTerm() { return term; }
    public void setTerm(String term) { this.term = term; }
    public boolean isRegistrationClosed() { return registrationClosed; }
    public void setRegistrationClosed(boolean registrationClosed) { this.registrationClosed = registrationClosed; }
}
