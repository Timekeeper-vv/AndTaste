package com.example.shixun.mapper;

import com.example.shixun.model.Pen;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface PenMapper {
    List<Pen> findAll();
    Pen findById(Long id);
    List<Pen> findAllActive();
    int insert(Pen pen);
    int update(Pen pen);
    int deleteById(Long id);
    int incrementCount(Long penId);
    int decrementCount(Long penId);
}
