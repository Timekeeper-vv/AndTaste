package com.example.shixun.mapper;

import com.example.shixun.model.Animal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface AnimalMapper {
    List<Animal> findAll();
    Animal findById(Long id);
    Animal findByEarTag(String earTag);
    List<Animal> findByBatchId(Long batchId);
    int insert(Animal animal);
    int update(Animal animal);
    int deleteById(Long id);
    int updateCurrentPen(@Param("earTag") String earTag, @Param("penId") Long penId);
    int updateStatus(@Param("earTag") String earTag, @Param("status") String status);
    boolean existsByEarTag(String earTag);
    int countByEarTagPrefix(String prefix);
}
