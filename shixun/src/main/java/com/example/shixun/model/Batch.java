package com.example.shixun.model;

public class Batch {
    private Long id;
    private String batchCode;
    private String entryDate;
    private String breed;
    private String source;
    private Long initialPenId;
    private String notes;
    private String createdAt;
    // 关联展示字段
    private String initialPenName;
    private Integer animalCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBatchCode() { return batchCode; }
    public void setBatchCode(String batchCode) { this.batchCode = batchCode; }
    public String getEntryDate() { return entryDate; }
    public void setEntryDate(String entryDate) { this.entryDate = entryDate; }
    public String getBreed() { return breed; }
    public void setBreed(String breed) { this.breed = breed; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Long getInitialPenId() { return initialPenId; }
    public void setInitialPenId(Long initialPenId) { this.initialPenId = initialPenId; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getInitialPenName() { return initialPenName; }
    public void setInitialPenName(String initialPenName) { this.initialPenName = initialPenName; }
    public Integer getAnimalCount() { return animalCount; }
    public void setAnimalCount(Integer animalCount) { this.animalCount = animalCount; }
}
