package com.wangyue.backend.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private final SecretKey signingKey;
    private final long accessTokenExpiresInSeconds;

    public JwtTokenService(
        @Value("${app.jwt.secret}") String base64Secret,
        @Value("${app.jwt.access-token-expires-in-seconds}") long accessTokenExpiresInSeconds
    ) {
        if (base64Secret == null || base64Secret.isBlank()) {
            throw new IllegalStateException("未配置 app.jwt.secret，后端不能安全启动");
        }
        if (accessTokenExpiresInSeconds <= 0) {
            throw new IllegalStateException("JWT 过期时间必须大于 0");
        }

        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(base64Secret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("app.jwt.secret 必须是有效的 Base64 字符串", exception);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret 解码后至少需要 32 字节");
        }

        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenExpiresInSeconds = accessTokenExpiresInSeconds;
    }

    public String createAccessToken(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("不能为缺少 ID 的用户创建 JWT");
        }

        Instant issuedAt = Instant.now();
        return Jwts.builder()
            .subject(userId.toString())
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(issuedAt.plusSeconds(accessTokenExpiresInSeconds)))
            .signWith(signingKey)
            .compact();
    }

    /**
     * Verifies the JWT signature and expiry, then returns the user id that was
     * written into the token subject at login time.
     */
    public Long parseAccessTokenUserId(String accessToken) {
        String subject = Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(accessToken)
            .getPayload()
            .getSubject();

        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("JWT does not contain a user id");
        }

        Long userId = Long.valueOf(subject);
        if (userId <= 0) {
            throw new IllegalArgumentException("JWT user id must be positive");
        }
        return userId;
    }

    public long getAccessTokenExpiresInSeconds() {
        return accessTokenExpiresInSeconds;
    }
}
