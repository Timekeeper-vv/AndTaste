package com.example.shixun.mapper;

import com.example.shixun.model.PenTransferRecord;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface PenTransferMapper {
    List<PenTransferRecord> findAll();
    List<PenTransferRecord> findByEarTag(String earTag);
    int insert(PenTransferRecord record);
    int deleteById(Long id);
}
