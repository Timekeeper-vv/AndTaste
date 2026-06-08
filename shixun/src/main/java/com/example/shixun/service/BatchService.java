package com.example.shixun.service;

import com.example.shixun.mapper.BatchMapper;
import com.example.shixun.model.Batch;
import org.springframework.stereotype.Service;
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

    public Batch create(Batch batch) {
        if (batchMapper.findByBatchCode(batch.getBatchCode()) != null) {
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
