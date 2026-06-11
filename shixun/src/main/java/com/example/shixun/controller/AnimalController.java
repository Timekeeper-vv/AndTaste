package com.example.shixun.controller;

import com.example.shixun.model.Animal;
import com.example.shixun.service.AnimalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/animals")
@Tag(name = "个体数字档案", description = "FR-04 以耳标号为唯一标识，构建牲畜终身数字身份")
public class AnimalController {

    private final AnimalService animalService;

    public AnimalController(AnimalService animalService) {
        this.animalService = animalService;
    }

    @GetMapping
    @Operation(summary = "获取所有个体档案")
    public List<Animal> findAll() {
        return animalService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据ID查询个体")
    public ResponseEntity<Animal> findById(@PathVariable Long id) {
        Animal animal = animalService.findById(id);
        if (animal == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "个体档案不存在");
        return ResponseEntity.ok(animal);
    }

    @GetMapping("/generate-ear-tag")
    @Operation(summary = "生成下一个可用耳标号")
    public ResponseEntity<Map<String, String>> generateEarTag() {
        return ResponseEntity.ok(Map.of("earTag", animalService.generateEarTag()));
    }

    @GetMapping("/ear-tag/{earTag}")
    @Operation(summary = "根据耳标号精确查询个体")
    public ResponseEntity<Animal> findByEarTag(@PathVariable String earTag) {
        Animal animal = animalService.findByEarTag(earTag);
        if (animal == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "耳标号不存在: " + earTag);
        return ResponseEntity.ok(animal);
    }

    @PostMapping
    @Operation(summary = "新建个体档案（耳标号可自动生成，全局唯一）")
    public ResponseEntity<?> create(@RequestBody Animal animal) {
        if (animal.getBreed() == null || animal.getBreed().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "品种不能为空");
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(animalService.create(animal));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新个体信息")
    public ResponseEntity<Animal> update(@PathVariable Long id, @RequestBody Animal animal) {
        Animal updated = animalService.update(id, animal);
        if (updated == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "个体档案不存在");
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除个体档案")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!animalService.delete(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "个体档案不存在");
        return ResponseEntity.noContent().build();
    }
}
