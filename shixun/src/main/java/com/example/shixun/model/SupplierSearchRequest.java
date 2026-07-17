package com.example.shixun.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SupplierSearchRequest {
    private String region;
    private String keyword;

    @JsonProperty("bank_name")
    @JsonAlias("bankName")
    private String bankName;

    @JsonProperty("is_count_only")
    @JsonAlias("countOnly")
    private Boolean countOnly;

    private Integer limit;

    public SupplierSearchRequest() {}

    public SupplierSearchRequest(String region, String keyword, String bankName, Boolean countOnly, Integer limit) {
        this.region = region;
        this.keyword = keyword;
        this.bankName = bankName;
        this.countOnly = countOnly;
        this.limit = limit;
    }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public Boolean getCountOnly() { return countOnly; }
    public void setCountOnly(Boolean countOnly) { this.countOnly = countOnly; }
    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }
}
