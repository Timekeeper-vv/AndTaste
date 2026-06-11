package com.example.shixun.service;

import com.example.shixun.mapper.*;
import com.example.shixun.model.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class FarmEventService {

    private final ImmunizationMapper immunizationMapper;
    private final MedicationMapper medicationMapper;
    private final PenTransferMapper penTransferMapper;
    private final SlaughterMapper slaughterMapper;
    private final DeathMapper deathMapper;
    private final AnimalMapper animalMapper;
    private final PenMapper penMapper;

    public FarmEventService(ImmunizationMapper immunizationMapper,
                            MedicationMapper medicationMapper,
                            PenTransferMapper penTransferMapper,
                            SlaughterMapper slaughterMapper,
                            DeathMapper deathMapper,
                            AnimalMapper animalMapper,
                            PenMapper penMapper) {
        this.immunizationMapper = immunizationMapper;
        this.medicationMapper = medicationMapper;
        this.penTransferMapper = penTransferMapper;
        this.slaughterMapper = slaughterMapper;
        this.deathMapper = deathMapper;
        this.animalMapper = animalMapper;
        this.penMapper = penMapper;
    }

    // ── 免疫记录 ─────────────────────────────────────────
    public List<ImmunizationRecord> findAllImmunization() {
        return immunizationMapper.findAll();
    }

    public List<ImmunizationRecord> findImmunizationByEarTag(String earTag) {
        return immunizationMapper.findByEarTag(earTag);
    }

    public ImmunizationRecord addImmunization(ImmunizationRecord record) {
        validateAnimalActive(record.getEarTag());
        immunizationMapper.insert(record);
        return record;
    }

    public boolean deleteImmunization(Long id) {
        return immunizationMapper.deleteById(id) > 0;
    }

    // ── 用药记录 ─────────────────────────────────────────
    public List<MedicationRecord> findAllMedication() {
        return medicationMapper.findAll();
    }

    public List<MedicationRecord> findMedicationByEarTag(String earTag) {
        return medicationMapper.findByEarTag(earTag);
    }

    public MedicationRecord addMedication(MedicationRecord record) {
        validateAnimalActive(record.getEarTag());
        medicationMapper.insert(record);
        return record;
    }

    public boolean deleteMedication(Long id) {
        return medicationMapper.deleteById(id) > 0;
    }

    // ── 转舍管理（事务保障数据一致性）────────────────────
    public List<PenTransferRecord> findAllTransfer() {
        return penTransferMapper.findAll();
    }

    public List<PenTransferRecord> findTransferByEarTag(String earTag) {
        return penTransferMapper.findByEarTag(earTag);
    }

    @Transactional
    public PenTransferRecord transfer(PenTransferRecord record) {
        Animal animal = animalMapper.findByEarTag(record.getEarTag());
        if (animal == null) throw new IllegalArgumentException("耳标号不存在: " + record.getEarTag());
        if ("SOLD".equals(animal.getStatus())) throw new IllegalArgumentException("该个体已出栏，无法转舍");
        if ("DEAD".equals(animal.getStatus())) throw new IllegalArgumentException("该个体已死亡，无法转舍");
        Pen targetPen = penMapper.findById(record.getToPenId());
        if (targetPen == null || targetPen.getStatus() != 1) {
            throw new IllegalArgumentException("目标圈舍不存在或已停用");
        }

        Long fromPenId = animal.getCurrentPenId();
        record.setFromPenId(fromPenId);

        // 原子操作：①插入转舍记录 ②更新个体当前圈舍 ③调整存栏计数
        penTransferMapper.insert(record);
        animalMapper.updateCurrentPen(record.getEarTag(), record.getToPenId());
        if (fromPenId != null) penMapper.decrementCount(fromPenId);
        penMapper.incrementCount(record.getToPenId());

        return record;
    }

    public boolean deleteTransfer(Long id) {
        return penTransferMapper.deleteById(id) > 0;
    }

    // ── 出栏管理 ─────────────────────────────────────────
    public List<SlaughterRecord> findAllSlaughter() {
        return slaughterMapper.findAll();
    }

    public List<SlaughterRecord> findSlaughterByEarTag(String earTag) {
        return slaughterMapper.findByEarTag(earTag);
    }

    @Transactional
    public SlaughterRecord slaughter(SlaughterRecord record) {
        Animal animal = animalMapper.findByEarTag(record.getEarTag());
        if (animal == null) throw new IllegalArgumentException("耳标号不存在: " + record.getEarTag());
        if ("SOLD".equals(animal.getStatus())) throw new IllegalArgumentException("该个体已出栏");

        // 原子操作：①插入出栏记录 ②更新个体状态 ③调整存栏计数
        slaughterMapper.insert(record);
        animalMapper.updateStatus(record.getEarTag(), "SOLD");
        if (animal.getCurrentPenId() != null) {
            penMapper.decrementCount(animal.getCurrentPenId());
        }

        return record;
    }

    public boolean deleteSlaughter(Long id) {
        return slaughterMapper.deleteById(id) > 0;
    }

    // ── 死亡管理 ─────────────────────────────────────────
    public List<DeathRecord> findAllDeath() {
        return deathMapper.findAll();
    }

    public List<DeathRecord> findDeathByEarTag(String earTag) {
        return deathMapper.findByEarTag(earTag);
    }

    @Transactional
    public DeathRecord recordDeath(DeathRecord record) {
        Animal animal = animalMapper.findByEarTag(record.getEarTag());
        if (animal == null) throw new IllegalArgumentException("耳标号不存在: " + record.getEarTag());
        if ("SOLD".equals(animal.getStatus())) throw new IllegalArgumentException("该个体已出栏");
        if ("DEAD".equals(animal.getStatus())) throw new IllegalArgumentException("该个体已记录死亡");

        deathMapper.insert(record);
        animalMapper.updateStatus(record.getEarTag(), "DEAD");
        if (animal.getCurrentPenId() != null) {
            penMapper.decrementCount(animal.getCurrentPenId());
        }
        return record;
    }

    public boolean deleteDeath(Long id) {
        return deathMapper.deleteById(id) > 0;
    }

    private void validateAnimalActive(String earTag) {
        Animal animal = animalMapper.findByEarTag(earTag);
        if (animal == null) throw new IllegalArgumentException("耳标号不存在: " + earTag);
        if ("SOLD".equals(animal.getStatus())) throw new IllegalArgumentException("该个体已出栏，无法录入事件");
        if ("DEAD".equals(animal.getStatus())) throw new IllegalArgumentException("该个体已死亡，无法录入事件");
    }
}
