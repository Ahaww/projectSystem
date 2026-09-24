package edu.wylie.crs.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 本系统缓存的开课班次快照。权威数据来自遗留课程目录（只读），
 * 座位占用、任教教授、取消标记等运行时状态写在本表。
 */
@Entity
@Table(name = "course_offering")
public class CourseOffering {

    @Id
    @Column(length = 32)
    private String id;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(length = 64)
    private String dept;

    @Column(length = 32)
    private String professorId;

    /** 目录快照中的授课显示名；任教结果以 professorId 为准 */
    @Column(length = 64)
    private String professorName;

    /** JSON 数组，如 ["周一","周三"] */
    @Column(length = 128)
    private String daysJson;

    private Integer startMinute;
    private Integer endMinute;

    @Column(length = 64)
    private String room;

    private Integer seatsTotal;
    private Integer seatsTaken;

    /** JSON 数组，先修课代码 */
    @Column(length = 256)
    private String prerequisitesJson;

    @Column(length = 32)
    private String term;

    private boolean cancelled;
    private boolean offeringClosed;

    /** 成绩单学分；目录快照无该字段时默认 3 */
    private Integer credits;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDept() { return dept; }
    public void setDept(String dept) { this.dept = dept; }
    public String getProfessorId() { return professorId; }
    public void setProfessorId(String professorId) { this.professorId = professorId; }
    public String getProfessorName() { return professorName; }
    public void setProfessorName(String professorName) { this.professorName = professorName; }
    public String getDaysJson() { return daysJson; }
    public void setDaysJson(String daysJson) { this.daysJson = daysJson; }
    public Integer getStartMinute() { return startMinute; }
    public void setStartMinute(Integer startMinute) { this.startMinute = startMinute; }
    public Integer getEndMinute() { return endMinute; }
    public void setEndMinute(Integer endMinute) { this.endMinute = endMinute; }
    public String getRoom() { return room; }
    public void setRoom(String room) { this.room = room; }
    public Integer getSeatsTotal() { return seatsTotal; }
    public void setSeatsTotal(Integer seatsTotal) { this.seatsTotal = seatsTotal; }
    public Integer getSeatsTaken() { return seatsTaken; }
    public void setSeatsTaken(Integer seatsTaken) { this.seatsTaken = seatsTaken; }
    public String getPrerequisitesJson() { return prerequisitesJson; }
    public void setPrerequisitesJson(String prerequisitesJson) { this.prerequisitesJson = prerequisitesJson; }
    public String getTerm() { return term; }
    public void setTerm(String term) { this.term = term; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    public boolean isOfferingClosed() { return offeringClosed; }
    public void setOfferingClosed(boolean offeringClosed) { this.offeringClosed = offeringClosed; }
    public Integer getCredits() { return credits; }
    public void setCredits(Integer credits) { this.credits = credits; }
}
