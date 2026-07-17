package com.example.shixun;

import com.example.shixun.mapper.UserMapper;
import com.example.shixun.model.User;
import com.example.shixun.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ShixunApplicationTests {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserService userService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final List<Long> createdUserIds = new ArrayList<>();

    // ========== 基础 ==========

    @Test
    void contextLoads() {
        assertThat(userMapper).isNotNull();
    }

    // ========== User Mapper ==========

    @Test
    void testUserFindAll() {
        List<User> users = userMapper.findAll();
        System.out.println(users);
        assertThat(users).isNotNull();
    }

    @Test
    void testUserInsert() {
        User user = new User(null, "charlie", 26, "charlie@test.com", "13800000003");
        user.setPassword("pass123");

        int rows = userMapper.insert(user);
        System.out.println(user);

        createdUserIds.add(user.getId());
        assertThat(rows).isEqualTo(1);
        assertThat(user.getId()).isNotNull();
    }

    @Test
    void testUserFindById() {
        Long id = createUser("charlie", 26, "charlie@test.com", "13800000003", "pass123");

        User user = userMapper.findById(id);
        System.out.println(user);

        assertThat(user).isNotNull();
        assertThat(user.getId()).isEqualTo(id);
        assertThat(user.getUsername()).isEqualTo("charlie");
    }

    @Test
    void testUserUpdate() {
        Long id = createUser("charlie", 26, "charlie@test.com", "13800000003", "pass123");

        User user = new User(id, "charlie-updated", 27, "charlie.updated@test.com", "13800000099");
        int rows = userMapper.update(user);
        User updatedUser = userMapper.findById(id);
        System.out.println(updatedUser);

        assertThat(rows).isEqualTo(1);
        assertThat(updatedUser).isNotNull();
        assertThat(updatedUser.getUsername()).isEqualTo("charlie-updated");
        assertThat(updatedUser.getAge()).isEqualTo(27);
        assertThat(updatedUser.getPhone()).isEqualTo("13800000099");
    }

    @Test
    void testUserDelete() {
        Long id = createUser("charlie", 26, "charlie@test.com", "13800000003", "pass123");

        int rows = userMapper.deleteById(id);
        User user = userMapper.findById(id);
        System.out.println(user);

        createdUserIds.remove(id);
        assertThat(rows).isEqualTo(1);
        assertThat(user).isNull();
    }

    // ========== 登录测试 ==========

    @Test
    void testLoginSuccess() {
        Long id = createUser("loginuser", 25, "login@test.com", "13900000001", "secret");

        User result = userService.login("loginuser", "secret").join();
        System.out.println(result);

        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("loginuser");
        assertThat(result.getId()).isEqualTo(id);
    }

    @Test
    void testLoginPasswordNullInResponse() {
        // 登录成功后密码字段必须为 null，不能把哈希值暴露给调用方
        createUser("loginuser_np", 25, "login_np@test.com", "13900000011", "secret");

        User result = userService.login("loginuser_np", "secret").join();

        assertThat(result).isNotNull();
        assertThat(result.getPassword()).isNull();
    }

    @Test
    void testLoginWrongPassword() {
        createUser("loginuser2", 25, "login2@test.com", "13900000002", "secret");

        User result = userService.login("loginuser2", "wrongpass").join();

        assertThat(result).isNull();
    }

    @Test
    void testLoginUserNotFound() {
        User result = userService.login("nonexistent", "anypass").join();

        assertThat(result).isNull();
    }

    @Test
    void testLoginBlankUsername() {
        // @Async 方法内抛出的异常被包装为 CompletionException
        assertThatThrownBy(() -> userService.login("", "anypass").join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("用户名不能为空");
    }

    @Test
    void testLoginNullUsername() {
        assertThatThrownBy(() -> userService.login(null, "anypass").join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("用户名不能为空");
    }

    @Test
    void testLoginEmptyPassword() {
        assertThatThrownBy(() -> userService.login("someuser", "").join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("密码不能为空");
    }

    @Test
    void testLoginNullPassword() {
        assertThatThrownBy(() -> userService.login("someuser", null).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("密码不能为空");
    }

    // ========== 注册测试 ==========

    @Test
    void testRegisterSuccess() {
        User user = new User(null, "newuser", 28, "new@test.com", "13700000001");
        user.setPassword("mypassword");

        User saved = userService.save(user).join();
        System.out.println(saved);

        createdUserIds.add(saved.getId());
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUsername()).isEqualTo("newuser");
        assertThat(userMapper.findById(saved.getId())).isNotNull();
    }

    @Test
    void testRegisterPasswordNullInResponse() {
        // 注册成功后返回的用户对象密码必须为 null
        User user = new User(null, "newuser_np", 28, "new_np@test.com", "13700000002");
        user.setPassword("mypassword");

        User saved = userService.save(user).join();
        createdUserIds.add(saved.getId());

        assertThat(saved.getPassword()).isNull();
    }

    @Test
    void testRegisterPasswordIsHashed() {
        // 数据库中存储的必须是 BCrypt 哈希，而非明文
        User user = new User(null, "hashuser", 28, "hash@test.com", "13700000003");
        user.setPassword("plaintext");

        User saved = userService.save(user).join();
        createdUserIds.add(saved.getId());

        User fromDb = userMapper.findById(saved.getId());
        assertThat(fromDb.getPassword()).isNotEqualTo("plaintext");
        assertThat(passwordEncoder.matches("plaintext", fromDb.getPassword())).isTrue();
    }

    @Test
    void testRegisterDuplicateUsername() {
        createUser("dupuser", 25, "dup@test.com", "13700000004", "pass");

        User dup = new User(null, "dupuser", 26, "dup2@test.com", "13700000005");
        dup.setPassword("pass2");

        assertThatThrownBy(() -> userService.save(dup).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("用户名已存在");
    }

    @Test
    void testRegisterAndLoginRoundTrip() {
        // 注册后用相同密码能登录，用错误密码不能登录
        User user = new User(null, "roundtrip", 30, "rt@test.com", "13700000006");
        user.setPassword("rt_pass");
        User saved = userService.save(user).join();
        createdUserIds.add(saved.getId());

        assertThat(userService.login("roundtrip", "rt_pass").join()).isNotNull();
        assertThat(userService.login("roundtrip", "wrong").join()).isNull();
    }

    // ========== 生命周期 ==========

    @AfterEach
    void tearDown() {
        createdUserIds.forEach(userMapper::deleteById);
        createdUserIds.clear();
    }

    @BeforeEach
    void logTestStart(TestInfo testInfo) {
        System.out.println("\n========== START " + testInfo.getDisplayName() + " ==========");
    }

    // ========== 辅助方法 ==========

    private Long createUser(String username, Integer age, String email, String phone, String password) {
        User user = new User(null, username, age, email, phone);
        user.setPassword(password);
        User saved = userService.save(user).join();
        createdUserIds.add(saved.getId());
        return saved.getId();
    }
}
