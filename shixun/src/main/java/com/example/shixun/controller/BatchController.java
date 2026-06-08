package com.example.shixun.controller;

import com.example.shixun.model.Batch;
import com.example.shixun.service.BatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/batches")
@Tag(name = "养殖批次", description = "FR-03 按同进同出原则对牲畜进行逻辑分组管理")
public class BatchController {

    private final BatchService batchService;

    public BatchController(BatchService batchService) {
        this.batchService = batchService;
    }

    @GetMapping
    @Operation(summary = "获取所有养殖批次")
    public List<Batch> findAll() {
        return batchService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID获取批次")
    public ResponseEntity<Batch> findById(@PathVariable Long id) {
        Batch batch = batchService.findById(id);
        if (batch == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "批次不存在");
        return ResponseEntity.ok(batch);
    }

    @PostMapping
    @Operation(summary = "新建养殖批次")
    public ResponseEntity<?> create(@RequestBody Batch batch) {
        if (batch.getBatchCode() == null || batch.getBatchCode().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "批次号不能为空");
        if (batch.getBreed() == null || batch.getBreed().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "品种不能为空");
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(batchService.create(batch));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新批次信息")
    public ResponseEntity<Batch> update(@PathVariable Long id, @RequestBody Batch batch) {
        Batch updated = batchService.update(id, batch);
        if (updated == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "批次不存在");
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除批次")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!batchService.delete(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "批次不存在");
        return ResponseEntity.noContent().build();
    }
}
