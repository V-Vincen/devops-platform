package com.vincent.devops.auth.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.vincent.devops.auth.domain.UserEntity;
import com.vincent.devops.auth.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 首次启动管理员初始化器。
 *
 * <p>只有显式提供管理员或开发人员的初始化密码，且数据库中不存在对应用户时
 * 才会创建账号，避免在代码或迁移脚本中写入默认密码。</p>
 */
@Component
public class BootstrapAdminRunner implements ApplicationRunner {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;
    private final String developerUsername;
    private final String developerPassword;

    public BootstrapAdminRunner(
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            @Value("${devops.security.bootstrap-admin-username:admin}") String adminUsername,
            @Value("${devops.security.bootstrap-admin-password:}") String adminPassword,
            @Value("${devops.security.bootstrap-developer-username:}") String developerUsername,
            @Value("${devops.security.bootstrap-developer-password:}") String developerPassword
    ) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.developerUsername = developerUsername;
        this.developerPassword = developerPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createIfConfigured(adminUsername, adminPassword, "平台管理员", "ADMIN");
        createIfConfigured(developerUsername, developerPassword, "开发人员", "DEVELOPER");
    }

    private void createIfConfigured(String username, String password, String displayName, String role) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            return;
        }

        UserEntity existing = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, username));
        if (existing != null) {
            return;
        }

        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setDeleted(false);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            // 多实例同时首次启动时，唯一索引保证只有一个实例创建成功。
        }
    }
}
