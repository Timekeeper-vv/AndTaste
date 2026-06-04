package com.example.shixun.mapper;

import com.example.shixun.model.User;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface UserMapper {
    List<User> findAll();
    User findById(Long id);
    User findByUsername(String username);
    int insert(User user);
    int update(User user);
    int deleteById(Long id);
}
