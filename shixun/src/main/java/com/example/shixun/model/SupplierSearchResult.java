package com.example.shixun.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SupplierSearchResult {
    private Long count;
    private Long total;
    private List<SupplierBankAccount> items;
    private List<String> names;

    @JsonProperty("query_params")
    private Map<String, Object> queryParams;

    public SupplierSearchResult() {}

    public static SupplierSearchResult count(long count, List<String> names, Map<String, Object> queryParams) {
        SupplierSearchResult r = new SupplierSearchResult();
        r.count = count;
        r.names = names;
        r.queryParams = queryParams;
        return r;
    }

    public static SupplierSearchResult list(long total, List<SupplierBankAccount> items, Map<String, Object> queryParams) {
        SupplierSearchResult r = new SupplierSearchResult();
        r.total = total;
        r.items = items;
        r.queryParams = queryParams;
        return r;
    }

    public Long getCount() { return count; }
    public void setCount(Long count) { this.count = count; }
    public Long getTotal() { return total; }
    public void setTotal(Long total) { this.total = total; }
    public List<SupplierBankAccount> getItems() { return items; }
    public void setItems(List<SupplierBankAccount> items) { this.items = items; }
    public List<String> getNames() { return names; }
    public void setNames(List<String> names) { this.names = names; }
    public Map<String, Object> getQueryParams() { return queryParams; }
    public void setQueryParams(Map<String, Object> queryParams) { this.queryParams = queryParams; }
}
