package com.example.shixun.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SupplierStatisticsRequest {
    @JsonProperty("group_by_field")
    @JsonAlias("groupByField")
    private String groupByField;

    @JsonProperty("include_count")
    @JsonAlias("includeCount")
    private Boolean includeCount;

    public SupplierStatisticsRequest() {}

    public SupplierStatisticsRequest(String groupByField, Boolean includeCount) {
        this.groupByField = groupByField;
        this.includeCount = includeCount;
    }

    public String getGroupByField() { return groupByField; }
    public void setGroupByField(String groupByField) { this.groupByField = groupByField; }
    public Boolean getIncludeCount() { return includeCount; }
    public void setIncludeCount(Boolean includeCount) { this.includeCount = includeCount; }
}
