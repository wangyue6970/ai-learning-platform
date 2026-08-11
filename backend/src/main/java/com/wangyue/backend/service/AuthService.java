package com.wangyue.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wangyue.backend.dto.RegisterRequest;
import com.wangyue.backend.entity.AppUser;
import com.wangyue.backend.mapper.AppUserMapper;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AppUserMapper appUserMapper;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AppUserMapper appUserMapper, PasswordEncoder passwordEncoder) {
        this.appUserMapper = appUserMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public AppUser register(RegisterRequest request) {
        validateRegisterRequest(request);

        String username = request.getUsername().trim();
        if (findByUsername(username) != null) {
            throw new IllegalArgumentException("用户名已存在");
        }

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        appUserMapper.insert(user);
        return user;
    }

    public AppUser findByUsername(String username) {
        return appUserMapper.selectOne(
            new LambdaQueryWrapper<AppUser>().eq(AppUser::getUsername, username)
        );
    }

    private void validateRegisterRequest(RegisterRequest request) {
        if (request == null || request.getUsername() == null) {
            throw new IllegalArgumentException("用户名不能为空");
        }

        String username = request.getUsername().trim();
        if (username.length() < 3 || username.length() > 50) {
            throw new IllegalArgumentException("用户名长度必须为 3 到 50 个字符");
        }

        String password = request.getPassword();
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        if (password.length() < 8) {
            throw new IllegalArgumentException("密码至少需要 8 个字符");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("密码长度不能超过 72 个 UTF-8 字节");
        }
    }
}
