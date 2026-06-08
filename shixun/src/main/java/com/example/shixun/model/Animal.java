package com.example.shixun.model;

public class Animal {
    private Long id;
    private String earTag;
    private String gender;
    private String entryDate;
    private String breed;
    private Long batchId;
    private Long currentPenId;
    private Double birthWeight;
    private String status;
    private String createdAt;
    // 关联展示字段
    private String batchCode;
    private String currentPenName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEarTag() { return earTag; }
    public void setEarTag(String earTag) { this.earTag = earTag; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getEntryDate() { return entryDate; }
    public void setEntryDate(String entryDate) { this.entryDate = entryDate; }
    public String getBreed() { return breed; }
    public void setBreed(String breed) { this.breed = breed; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Long getCurrentPenId() { return currentPenId; }
    public void setCurrentPenId(Long currentPenId) { this.currentPenId = currentPenId; }
    public Double getBirthWeight() { return birthWeight; }
    public void setBirthWeight(Double birthWeight) { this.birthWeight = birthWeight; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getBatchCode() { return batchCode; }
    public void setBatchCode(String batchCode) { this.batchCode = batchCode; }
    public String getCurrentPenName() { return currentPenName; }
    public void setCurrentPenName(String currentPenName) { this.currentPenName = currentPenName; }
}
