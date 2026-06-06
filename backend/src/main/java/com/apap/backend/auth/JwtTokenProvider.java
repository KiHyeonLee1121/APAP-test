package com.apap.backend.auth;

import com.apap.backend.user.User;
import com.apap.backend.user.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT(HS256) 발급/검증. subject=userId, 추가 클레임 email/name/role.
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecretKey key;
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${apap.jwt.secret:}") String secret,
            @Value("${apap.jwt.expiration-ms:86400000}") long expirationMs
    ) {
        this.expirationMs = expirationMs;
        if (StringUtils.hasText(secret) && secret.getBytes(StandardCharsets.UTF_8).length >= 32) {
            this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        } else {
            // 개발 편의용: 비밀키 미설정 시 임시 키 생성 (부팅마다 달라져 기존 토큰은 무효화됨)
            log.warn("JWT_SECRET이 설정되지 않았거나 32바이트 미만입니다. 개발용 임시 키를 생성합니다. 운영에서는 반드시 JWT_SECRET을 설정하세요.");
            this.key = Jwts.SIG.HS256.key().build();
        }
    }

    public String createToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("name", user.getName())
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /**
     * 토큰을 검증하고 AuthUser로 변환한다. 유효하지 않으면 null을 반환한다.
     */
    public AuthUser parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Long userId = Long.valueOf(claims.getSubject());
            String email = claims.get("email", String.class);
            UserRole role = UserRole.valueOf(claims.get("role", String.class));
            return new AuthUser(userId, email, role);
        } catch (Exception e) {
            return null;
        }
    }
}
