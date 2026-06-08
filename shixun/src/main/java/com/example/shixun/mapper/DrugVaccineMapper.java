package com.example.shixun.mapper;

import com.example.shixun.model.DrugVaccine;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface DrugVaccineMapper {
    List<DrugVaccine> findAll();
    DrugVaccine findById(Long id);
    List<DrugVaccine> findByCategory(String category);
    int insert(DrugVaccine dv);
    int update(DrugVaccine dv);
    int deleteById(Long id);
}
