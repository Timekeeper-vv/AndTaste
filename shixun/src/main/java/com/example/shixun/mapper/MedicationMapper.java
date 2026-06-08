package com.example.shixun.mapper;

import com.example.shixun.model.MedicationRecord;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface MedicationMapper {
    List<MedicationRecord> findAll();
    List<MedicationRecord> findByEarTag(String earTag);
    int insert(MedicationRecord record);
    int deleteById(Long id);
}
