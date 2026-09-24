package edu.wylie.crs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "id_sequence")
public class IdSequence {

    @Id
    @Column(length = 8)
    private String prefix;

    @Column(name = "seq_value", nullable = false)
    private int lastValue;

    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix; }
    public int getLastValue() { return lastValue; }
    public void setLastValue(int lastValue) { this.lastValue = lastValue; }
}
