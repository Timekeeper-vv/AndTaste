package com.example.shixun.controller;

import com.example.shixun.model.Pen;
import com.example.shixun.service.PenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/pens")
@Tag(name = "圈舍管理", description = "FR-01 圈舍资产数字化建模与状态管理")
public class PenController {

    private final PenService penService;

    public PenController(PenService penService) {
        this.penService = penService;
    }

    @GetMapping
    @Operation(summary = "获取所有圈舍")
    public List<Pen> findAll() {
        return penService.findAll();
    }

    @GetMapping("/active")
    @Operation(summary = "获取启用圈舍列表（用于下拉选择）")
    public List<Pen> findAllActive() {
        return penService.findAllActive();
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID获取圈舍")
    public ResponseEntity<Pen> findById(@PathVariable Long id) {
        Pen pen = penService.findById(id);
        if (pen == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "圈舍不存在");
        return ResponseEntity.ok(pen);
    }

    @PostMapping
    @Operation(summary = "新增圈舍")
    public ResponseEntity<Pen> create(@RequestBody Pen pen) {
        if (pen.getPenCode() == null || pen.getPenCode().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "圈舍编号不能为空");
        if (pen.getPenName() == null || pen.getPenName().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "圈舍名称不能为空");
        return ResponseEntity.status(HttpStatus.CREATED).body(penService.create(pen));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新圈舍信息")
    public ResponseEntity<Pen> update(@PathVariable Long id, @RequestBody Pen pen) {
        Pen updated = penService.update(id, pen);
        if (updated == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "圈舍不存在");
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除圈舍")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!penService.delete(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "圈舍不存在");
        return ResponseEntity.noContent().build();
    }
}
