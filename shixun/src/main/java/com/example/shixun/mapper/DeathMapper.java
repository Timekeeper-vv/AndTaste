package com.example.shixun.mapper;

import com.example.shixun.model.DeathRecord;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface DeathMapper {
    List<DeathRecord> findAll();
    List<DeathRecord> findByEarTag(String earTag);
    int insert(DeathRecord record);
    int deleteById(Long id);
}
