package com.example.shixun.service;

import com.example.shixun.mapper.PenMapper;
import com.example.shixun.model.Pen;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class PenService {

    private final PenMapper penMapper;

    public PenService(PenMapper penMapper) {
        this.penMapper = penMapper;
    }

    public List<Pen> findAll() {
        return penMapper.findAll();
    }

    public List<Pen> findAllActive() {
        return penMapper.findAllActive();
    }

    public Pen findById(Long id) {
        return penMapper.findById(id);
    }

    public Pen create(Pen pen) {
        if (pen.getStatus() == null) pen.setStatus(1);
        penMapper.insert(pen);
        return pen;
    }

    public Pen update(Long id, Pen pen) {
        Pen existing = penMapper.findById(id);
        if (existing == null) return null;
        pen.setId(id);
        penMapper.update(pen);
        return penMapper.findById(id);
    }

    public boolean delete(Long id) {
        return penMapper.deleteById(id) > 0;
    }
}
