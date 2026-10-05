package com.vincent.devops.auth.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 平台基础安全配置。
 *
 * <p>生产或共享测试环境默认启用认证；本地排查旧接口时可以通过
 * {@code DEVOPS_SECURITY_ENABLED=false} 临时关闭请求拦截。</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * 提供 BCrypt 密码摘要器，数据库不保存明文密码。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 占位用户服务，阻止 Spring Boot 自动创建随机内存账号。
     * 平台登录由 AuthService 从 sys_user 表校验密码并签发令牌。
     */
    @Bean
    public UserDetailsService placeholderUserDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("平台用户由数据库认证");
        };
    }

    /**
     * 配置无状态令牌认证和基础角色权限。
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthTokenFilter authTokenFilter,
            @Value("${devops.security.enabled:true}") boolean securityEnabled
    ) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {
                })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(authTokenFilter, UsernamePasswordAuthenticationFilter.class)
                // 区分未登录和已登录但无权限，便于前端正确处理会话失效与权限不足。
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                writeSecurityError(response, 401, "请先登录"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeSecurityError(response, 403, "没有权限执行此操作")))
                .authorizeHttpRequests(authorize -> {
                    if (!securityEnabled) {
                        authorize.anyRequest().permitAll();
                        return;
                    }
                    authorize
                            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                            .requestMatchers("/api/v1/auth/login", "/actuator/health", "/error").permitAll()
                            .requestMatchers("/api/v1/users/**").hasRole("ADMIN")
                            .requestMatchers(HttpMethod.DELETE, "/api/v1/**").hasRole("ADMIN")
                            .anyRequest().authenticated();
                });
        return http.build();
    }

    /**
     * 输出统一错误结构，避免 Spring Security 默认 HTML 或纯文本响应被前端误判。
     */
    private static void writeSecurityError(
            jakarta.servlet.http.HttpServletResponse response,
            int status,
            String message
    ) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"success\":false,\"data\":null,\"message\":\"" + message + "\"}");
    }
}
