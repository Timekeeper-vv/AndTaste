package com.example.shixun.mapper;

import com.example.shixun.model.Animal;
import com.example.shixun.model.TraceabilityEvent;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Map;

@Mapper
public interface TraceabilityMapper {
    List<TraceabilityEvent> getAnimalTimeline(String earTag);
    Map<String, Object> getBatchSummary(String batchCode);
    List<Animal> getBatchAnimals(String batchCode);
}
