package com.example.shixun.service;

import com.example.shixun.mapper.DrugVaccineMapper;
import com.example.shixun.model.DrugVaccine;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class DrugVaccineService {

    private final DrugVaccineMapper mapper;

    public DrugVaccineService(DrugVaccineMapper mapper) {
        this.mapper = mapper;
    }

    public List<DrugVaccine> findAll() {
        return mapper.findAll();
    }

    public List<DrugVaccine> findByCategory(String category) {
        return mapper.findByCategory(category);
    }

    public DrugVaccine findById(Long id) {
        return mapper.findById(id);
    }

    public DrugVaccine create(DrugVaccine dv) {
        mapper.insert(dv);
        return dv;
    }

    public DrugVaccine update(Long id, DrugVaccine dv) {
        if (mapper.findById(id) == null) return null;
        dv.setId(id);
        mapper.update(dv);
        return mapper.findById(id);
    }

    public boolean delete(Long id) {
        return mapper.deleteById(id) > 0;
    }
}
