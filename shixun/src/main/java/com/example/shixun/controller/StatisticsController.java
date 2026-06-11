package com.example.shixun.controller;

import com.example.shixun.mapper.StatisticsMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stats")
@Tag(name = "数据统计", description = "可视化图表数据接口")
public class StatisticsController {

    private final StatisticsMapper mapper;

    public StatisticsController(StatisticsMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping("/monthly-slaughter")
    @Operation(summary = "近6个月出栏量月度统计")
    public List<Map<String, Object>> monthlySlaughter() {
        return mapper.monthlySlaughter();
    }

    @GetMapping("/monthly-immunization")
    @Operation(summary = "近6个月免疫记录月度统计")
    public List<Map<String, Object>> monthlyImmunization() {
        return mapper.monthlyImmunization();
    }

    @GetMapping("/animal-status")
    @Operation(summary = "个体状态分布（在栏/已出栏）")
    public List<Map<String, Object>> animalStatus() {
        return mapper.animalStatusCount();
    }

    @GetMapping("/pen-usage")
    @Operation(summary = "圈舍容量使用情况")
    public List<Map<String, Object>> penUsage() {
        return mapper.penUsage();
    }

    @GetMapping("/monthly-entry")
    @Operation(summary = "近6个月入栏量月度统计")
    public List<Map<String, Object>> monthlyEntry() {
        return mapper.monthlyEntry();
    }

    @GetMapping("/monthly-death")
    @Operation(summary = "近6个月死亡数月度统计")
    public List<Map<String, Object>> monthlyDeath() {
        return mapper.monthlyDeath();
    }
}
