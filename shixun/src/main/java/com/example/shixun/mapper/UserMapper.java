package com.example.shixun.mapper;

import com.example.shixun.model.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface UserMapper {
    List<User> findAll();
    User findById(Long id);
    User findByUsername(String username);
    List<User> findByEmail(String email);
    int insert(User user);
    int update(User user);
    int deleteById(Long id);
    List<User> findPage(@Param("search") String search, @Param("offset") int offset, @Param("size") int size);
    long countSearch(@Param("search") String search);
    int touchLastLogin(@Param("id") Long id);
}
