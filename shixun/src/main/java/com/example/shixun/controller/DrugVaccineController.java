package com.example.shixun.controller;

import com.example.shixun.model.DrugVaccine;
import com.example.shixun.service.DrugVaccineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/drugs-vaccines")
@Tag(name = "兽药疫苗库", description = "FR-02 投入品主数据管理，规范事件录入口径")
public class DrugVaccineController {

    private final DrugVaccineService service;

    public DrugVaccineController(DrugVaccineService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "获取所有兽药/疫苗")
    public List<DrugVaccine> findAll(@RequestParam(required = false) String category) {
        if (category != null && !category.isBlank()) return service.findByCategory(category);
        return service.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询")
    public ResponseEntity<DrugVaccine> findById(@PathVariable Long id) {
        DrugVaccine dv = service.findById(id);
        if (dv == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在");
        return ResponseEntity.ok(dv);
    }

    @PostMapping
    @Operation(summary = "新增兽药/疫苗")
    public ResponseEntity<DrugVaccine> create(@RequestBody DrugVaccine dv) {
        if (dv.getGenericName() == null || dv.getGenericName().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "通用名不能为空");
        if (dv.getCategory() == null || dv.getCategory().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分类不能为空");
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dv));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新兽药/疫苗信息")
    public ResponseEntity<DrugVaccine> update(@PathVariable Long id, @RequestBody DrugVaccine dv) {
        DrugVaccine updated = service.update(id, dv);
        if (updated == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在");
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除兽药/疫苗")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!service.delete(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在");
        return ResponseEntity.noContent().build();
    }
}
