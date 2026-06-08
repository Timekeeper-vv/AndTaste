package com.example.shixun.controller;

import com.example.shixun.service.TraceabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
@RequestMapping("/api/traceability")
@Tag(name = "全链路溯源", description = "FR-09/FR-10 个体全生命周期溯源与批次溯源概览")
public class TraceabilityController {

    private final TraceabilityService service;

    public TraceabilityController(TraceabilityService service) {
        this.service = service;
    }

    @GetMapping("/animal/{earTag}")
    @Operation(summary = "个体全生命周期溯源",
               description = "输入耳标号，返回基础档案+所有事件的时间线（UNION ALL多表关联查询）")
    public ResponseEntity<Map<String, Object>> getAnimalTraceability(@PathVariable String earTag) {
        Map<String, Object> result = service.getAnimalTraceability(earTag);
        if (result == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "耳标号不存在: " + earTag);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/batch/{batchCode}")
    @Operation(summary = "批次溯源概览",
               description = "输入批次号，返回批次统计仪表盘+个体列表")
    public ResponseEntity<Map<String, Object>> getBatchTraceability(@PathVariable String batchCode) {
        Map<String, Object> result = service.getBatchTraceability(batchCode);
        if (result == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "批次号不存在: " + batchCode);
        return ResponseEntity.ok(result);
    }
}
