package com.example.shixun.service;

import com.example.shixun.mapper.BatchMapper;
import com.example.shixun.model.Batch;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class BatchService {

    private final BatchMapper batchMapper;

    public BatchService(BatchMapper batchMapper) {
        this.batchMapper = batchMapper;
    }

    public List<Batch> findAll() {
        return batchMapper.findAll();
    }

    public Batch findById(Long id) {
        return batchMapper.findById(id);
    }

    public String generateBatchCode() {
        String prefix = "BATCH" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int count = batchMapper.countByBatchCodePrefix(prefix);
        String candidate = prefix + String.format("%03d", count + 1);
        while (batchMapper.findByBatchCode(candidate) != null) {
            count++;
            candidate = prefix + String.format("%03d", count + 1);
        }
        return candidate;
    }

    public Batch create(Batch batch) {
        if (batch.getBatchCode() == null || batch.getBatchCode().isBlank()) {
            batch.setBatchCode(generateBatchCode());
        } else if (batchMapper.findByBatchCode(batch.getBatchCode()) != null) {
            throw new IllegalArgumentException("批次号已存在: " + batch.getBatchCode());
        }
        batchMapper.insert(batch);
        return batchMapper.findById(batch.getId());
    }

    public Batch update(Long id, Batch batch) {
        if (batchMapper.findById(id) == null) return null;
        batch.setId(id);
        batchMapper.update(batch);
        return batchMapper.findById(id);
    }

    public boolean delete(Long id) {
        return batchMapper.deleteById(id) > 0;
    }
}
