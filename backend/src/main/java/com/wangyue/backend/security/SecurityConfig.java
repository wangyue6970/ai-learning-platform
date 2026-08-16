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
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfig {

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
                    writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "请先登录或重新登录")
                )
                .accessDeniedHandler((request, response, exception) ->
                    writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, exception.getMessage())
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

    private void writeJsonError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), new ApiErrorResponse(message));
    }
}
