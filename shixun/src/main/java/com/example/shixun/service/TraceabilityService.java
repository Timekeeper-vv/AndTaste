package com.example.shixun.service;

import com.example.shixun.mapper.AnimalMapper;
import com.example.shixun.mapper.TraceabilityMapper;
import com.example.shixun.model.Animal;
import com.example.shixun.model.TraceabilityEvent;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TraceabilityService {

    private final TraceabilityMapper traceabilityMapper;
    private final AnimalMapper animalMapper;

    public TraceabilityService(TraceabilityMapper traceabilityMapper, AnimalMapper animalMapper) {
        this.traceabilityMapper = traceabilityMapper;
        this.animalMapper = animalMapper;
    }

    public Map<String, Object> getAnimalTraceability(String earTag) {
        Animal animal = animalMapper.findByEarTag(earTag);
        if (animal == null) return null;
        List<TraceabilityEvent> timeline = traceabilityMapper.getAnimalTimeline(earTag);
        Map<String, Object> result = new HashMap<>();
        result.put("animal", animal);
        result.put("timeline", timeline);
        return result;
    }

    public Map<String, Object> getBatchTraceability(String batchCode) {
        Map<String, Object> batch = traceabilityMapper.getBatchSummary(batchCode);
        if (batch == null) return null;
        List<Animal> animals = traceabilityMapper.getBatchAnimals(batchCode);
        Map<String, Object> result = new HashMap<>();
        result.put("batch", batch);
        result.put("animals", animals);
        return result;
    }
}
