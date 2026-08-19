package com.wangyue.backend.security;

import com.wangyue.backend.dto.ApiErrorResponse;
import com.wangyue.backend.mapper.AppUserMapper;
import com.wangyue.backend.service.JwtTokenService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    private final JwtTokenService jwtTokenService;
    private final AppUserMapper appUserMapper;
    private final ObjectMapper objectMapper;

    public SecurityConfig(
        JwtTokenService jwtTokenService,
        AppUserMapper appUserMapper,
        ObjectMapper objectMapper
    ) {
        this.jwtTokenService = jwtTokenService;
        this.appUserMapper = appUserMapper;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // The app sends JWTs in request headers, not browser cookies.
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, exception) ->
                    writeJsonError(
                        response,
                        HttpServletResponse.SC_UNAUTHORIZED,
                        "请先登录或重新登录",
                        "下一步：请重新登录后再操作。"
                    )
                )
                .accessDeniedHandler((request, response, exception) ->
                    writeForbiddenError(request, response, exception)
                )
            )
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/auth/**", "/health").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            )
            .addFilterBefore(
                new JwtAuthenticationFilter(jwtTokenService, appUserMapper),
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }

    private void writeJsonError(
        HttpServletResponse response,
        int status,
        String message,
        String action
    ) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), new ApiErrorResponse(message, action));
    }

    private void writeForbiddenError(
        jakarta.servlet.http.HttpServletRequest request,
        HttpServletResponse response,
        Exception exception
    ) throws IOException {
        logger.warn("拒绝访问：{} {}，原因：{}", request.getMethod(), request.getRequestURI(), exception.getMessage());
        writeJsonError(
            response,
            HttpServletResponse.SC_FORBIDDEN,
            "无权访问该数据",
            "下一步：请返回自己的学习库后重新选择。"
        );
    }
}
