package com.example.shixun.model;

public class DeathRecord {
    private Long id;
    private String earTag;
    private String eventTime;
    private String cause;
    private String operator;
    private String notes;
    private String createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEarTag() { return earTag; }
    public void setEarTag(String earTag) { this.earTag = earTag; }
    public String getEventTime() { return eventTime; }
    public void setEventTime(String eventTime) { this.eventTime = eventTime; }
    public String getCause() { return cause; }
    public void setCause(String cause) { this.cause = cause; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
