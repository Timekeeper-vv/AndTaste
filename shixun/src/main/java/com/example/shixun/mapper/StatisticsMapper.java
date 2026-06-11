package com.example.shixun.mapper;

import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Map;

@Mapper
public interface StatisticsMapper {
    List<Map<String, Object>> monthlySlaughter();
    List<Map<String, Object>> monthlyImmunization();
    List<Map<String, Object>> animalStatusCount();
    List<Map<String, Object>> penUsage();
    List<Map<String, Object>> overcapacityPens();
    List<Map<String, Object>> immunizationDueAnimals();
    List<Map<String, Object>> monthlyEntry();
    List<Map<String, Object>> monthlyDeath();
}
