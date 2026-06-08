package com.example.shixun.service;

import com.example.shixun.mapper.AnimalMapper;
import com.example.shixun.mapper.PenMapper;
import com.example.shixun.model.Animal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class AnimalService {

    private final AnimalMapper animalMapper;
    private final PenMapper penMapper;

    public AnimalService(AnimalMapper animalMapper, PenMapper penMapper) {
        this.animalMapper = animalMapper;
        this.penMapper = penMapper;
    }

    public List<Animal> findAll() {
        return animalMapper.findAll();
    }

    public Animal findById(Long id) {
        return animalMapper.findById(id);
    }

    public Animal findByEarTag(String earTag) {
        return animalMapper.findByEarTag(earTag);
    }

    @Transactional
    public Animal create(Animal animal) {
        if (animalMapper.existsByEarTag(animal.getEarTag())) {
            throw new IllegalArgumentException("耳标号已存在: " + animal.getEarTag());
        }
        animalMapper.insert(animal);
        // 同步更新圈舍存栏计数
        if (animal.getCurrentPenId() != null) {
            penMapper.incrementCount(animal.getCurrentPenId());
        }
        return animalMapper.findByEarTag(animal.getEarTag());
    }

    public Animal update(Long id, Animal animal) {
        if (animalMapper.findById(id) == null) return null;
        animal.setId(id);
        animalMapper.update(animal);
        return animalMapper.findById(id);
    }

    public boolean delete(Long id) {
        return animalMapper.deleteById(id) > 0;
    }
}
