package com.example.shixun.mapper;

import com.example.shixun.model.SlaughterRecord;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface SlaughterMapper {
    List<SlaughterRecord> findAll();
    List<SlaughterRecord> findByEarTag(String earTag);
    int insert(SlaughterRecord record);
    int deleteById(Long id);
}
