package com.example.shixun.model;

public class Pen {
    private Long id;
    private String penCode;
    private String penName;
    private Integer capacity;
    private String responsiblePerson;
    private Integer status;
    private Integer currentCount;
    private String createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPenCode() { return penCode; }
    public void setPenCode(String penCode) { this.penCode = penCode; }
    public String getPenName() { return penName; }
    public void setPenName(String penName) { this.penName = penName; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public String getResponsiblePerson() { return responsiblePerson; }
    public void setResponsiblePerson(String responsiblePerson) { this.responsiblePerson = responsiblePerson; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getCurrentCount() { return currentCount; }
    public void setCurrentCount(Integer currentCount) { this.currentCount = currentCount; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
