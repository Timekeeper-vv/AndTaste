package com.example.shixun.controller;

import com.example.shixun.model.*;
import com.example.shixun.service.FarmEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/events")
@Tag(name = "生产过程记录", description = "FR-05~FR-08 免疫/用药/转舍/出栏事件录入")
public class FarmEventController {

    private final FarmEventService service;

    public FarmEventController(FarmEventService service) {
        this.service = service;
    }

    // ── 免疫记录 ──────────────────────────────────────────
    @GetMapping("/immunization")
    @Operation(summary = "查询免疫记录（可按耳标过滤）")
    public List<ImmunizationRecord> getImmunization(@RequestParam(required = false) String earTag) {
        if (earTag != null && !earTag.isBlank()) return service.findImmunizationByEarTag(earTag);
        return service.findAllImmunization();
    }

    @PostMapping("/immunization")
    @Operation(summary = "录入免疫记录")
    public ResponseEntity<?> addImmunization(@RequestBody ImmunizationRecord record) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(service.addImmunization(record));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/immunization/{id}")
    @Operation(summary = "删除免疫记录")
    public ResponseEntity<Void> deleteImmunization(@PathVariable Long id) {
        service.deleteImmunization(id);
        return ResponseEntity.noContent().build();
    }

    // ── 用药记录 ──────────────────────────────────────────
    @GetMapping("/medication")
    @Operation(summary = "查询用药记录（可按耳标过滤）")
    public List<MedicationRecord> getMedication(@RequestParam(required = false) String earTag) {
        if (earTag != null && !earTag.isBlank()) return service.findMedicationByEarTag(earTag);
        return service.findAllMedication();
    }

    @PostMapping("/medication")
    @Operation(summary = "录入用药记录")
    public ResponseEntity<?> addMedication(@RequestBody MedicationRecord record) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(service.addMedication(record));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/medication/{id}")
    @Operation(summary = "删除用药记录")
    public ResponseEntity<Void> deleteMedication(@PathVariable Long id) {
        service.deleteMedication(id);
        return ResponseEntity.noContent().build();
    }

    // ── 转舍管理 ──────────────────────────────────────────
    @GetMapping("/transfer")
    @Operation(summary = "查询转舍记录（可按耳标过滤）")
    public List<PenTransferRecord> getTransfer(@RequestParam(required = false) String earTag) {
        if (earTag != null && !earTag.isBlank()) return service.findTransferByEarTag(earTag);
        return service.findAllTransfer();
    }

    @PostMapping("/transfer")
    @Operation(summary = "执行转舍（事务保障数据一致性）")
    public ResponseEntity<?> transfer(@RequestBody PenTransferRecord record) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(service.transfer(record));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/transfer/{id}")
    @Operation(summary = "删除转舍记录")
    public ResponseEntity<Void> deleteTransfer(@PathVariable Long id) {
        service.deleteTransfer(id);
        return ResponseEntity.noContent().build();
    }

    // ── 出栏管理 ──────────────────────────────────────────
    @GetMapping("/slaughter")
    @Operation(summary = "查询出栏记录（可按耳标过滤）")
    public List<SlaughterRecord> getSlaughter(@RequestParam(required = false) String earTag) {
        if (earTag != null && !earTag.isBlank()) return service.findSlaughterByEarTag(earTag);
        return service.findAllSlaughter();
    }

    @PostMapping("/slaughter")
    @Operation(summary = "执行出栏登记（原子更新个体状态）")
    public ResponseEntity<?> slaughter(@RequestBody SlaughterRecord record) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(service.slaughter(record));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/slaughter/{id}")
    @Operation(summary = "删除出栏记录")
    public ResponseEntity<Void> deleteSlaughter(@PathVariable Long id) {
        service.deleteSlaughter(id);
        return ResponseEntity.noContent().build();
    }
}
