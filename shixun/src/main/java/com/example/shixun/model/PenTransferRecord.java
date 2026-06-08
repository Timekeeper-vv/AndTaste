package com.example.shixun.model;

public class PenTransferRecord {
    private Long id;
    private String earTag;
    private Long fromPenId;
    private Long toPenId;
    private String eventTime;
    private String reason;
    private String createdAt;
    // 关联展示字段
    private String fromPenName;
    private String toPenName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEarTag() { return earTag; }
    public void setEarTag(String earTag) { this.earTag = earTag; }
    public Long getFromPenId() { return fromPenId; }
    public void setFromPenId(Long fromPenId) { this.fromPenId = fromPenId; }
    public Long getToPenId() { return toPenId; }
    public void setToPenId(Long toPenId) { this.toPenId = toPenId; }
    public String getEventTime() { return eventTime; }
    public void setEventTime(String eventTime) { this.eventTime = eventTime; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getFromPenName() { return fromPenName; }
    public void setFromPenName(String fromPenName) { this.fromPenName = fromPenName; }
    public String getToPenName() { return toPenName; }
    public void setToPenName(String toPenName) { this.toPenName = toPenName; }
}
