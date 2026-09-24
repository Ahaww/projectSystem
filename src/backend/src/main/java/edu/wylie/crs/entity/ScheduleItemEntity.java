package edu.wylie.crs.entity;

import edu.wylie.crs.domain.ItemStatus;
import edu.wylie.crs.domain.ItemType;
import jakarta.persistence.*;

@Entity
@Table(name = "schedule_item")
public class ScheduleItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long scheduleId;

    @Column(nullable = false, length = 32)
    private String offeringId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 16)
    private ItemType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ItemStatus status;

    /** 备选优先级，1 最高 */
    private Integer priority;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getScheduleId() { return scheduleId; }
    public void setScheduleId(Long scheduleId) { this.scheduleId = scheduleId; }
    public String getOfferingId() { return offeringId; }
    public void setOfferingId(String offeringId) { this.offeringId = offeringId; }
    public ItemType getType() { return type; }
    public void setType(ItemType type) { this.type = type; }
    public ItemStatus getStatus() { return status; }
    public void setStatus(ItemStatus status) { this.status = status; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
}
