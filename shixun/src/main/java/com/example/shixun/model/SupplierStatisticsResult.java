package com.example.shixun.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SupplierStatisticsResult {
    private String field;
    private List<Group> groups;
    private String message;

    @JsonProperty("query_params")
    private Map<String, Object> queryParams;

    public SupplierStatisticsResult() {}

    public SupplierStatisticsResult(String field, List<Group> groups, String message, Map<String, Object> queryParams) {
        this.field = field;
        this.groups = groups;
        this.message = message;
        this.queryParams = queryParams;
    }

    public String getField() { return field; }
    public void setField(String field) { this.field = field; }
    public List<Group> getGroups() { return groups; }
    public void setGroups(List<Group> groups) { this.groups = groups; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Map<String, Object> getQueryParams() { return queryParams; }
    public void setQueryParams(Map<String, Object> queryParams) { this.queryParams = queryParams; }

    public static class Group {
        private String value;
        private Long count;

        public Group() {}

        public Group(String value, Long count) {
            this.value = value;
            this.count = count;
        }

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public Long getCount() { return count; }
        public void setCount(Long count) { this.count = count; }
    }
}
