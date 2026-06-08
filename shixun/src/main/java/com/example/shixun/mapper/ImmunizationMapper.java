package com.example.shixun.mapper;

import com.example.shixun.model.ImmunizationRecord;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface ImmunizationMapper {
    List<ImmunizationRecord> findAll();
    List<ImmunizationRecord> findByEarTag(String earTag);
    int insert(ImmunizationRecord record);
    int deleteById(Long id);
}
