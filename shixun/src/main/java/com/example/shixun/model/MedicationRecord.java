package com.example.shixun.model;

public class MedicationRecord {
    private Long id;
    private String earTag;
    private Long drugId;
    private String reason;
    private String eventTime;
    private String dosage;
    private String operator;
    private String createdAt;
    // 关联展示字段
    private String drugName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEarTag() { return earTag; }
    public void setEarTag(String earTag) { this.earTag = earTag; }
    public Long getDrugId() { return drugId; }
    public void setDrugId(Long drugId) { this.drugId = drugId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getEventTime() { return eventTime; }
    public void setEventTime(String eventTime) { this.eventTime = eventTime; }
    public String getDosage() { return dosage; }
    public void setDosage(String dosage) { this.dosage = dosage; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getDrugName() { return drugName; }
    public void setDrugName(String drugName) { this.drugName = drugName; }
}
