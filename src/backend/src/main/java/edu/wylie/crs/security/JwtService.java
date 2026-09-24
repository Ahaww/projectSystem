package edu.wylie.crs.security;

import edu.wylie.crs.common.ApiException;
import edu.wylie.crs.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtService {

    private final SecretKey key;
    private final long expireHours;

    public JwtService(
            @Value("${crs.jwt.secret}") String secret,
            @Value("${crs.jwt.expire-hours}") long expireHours
    ) {
        this.key = Keys.hmacShaKeyFor(pad(secret).getBytes(StandardCharsets.UTF_8));
        this.expireHours = expireHours;
    }

    public String issue(AuthUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.id())
                .claim("username", user.username())
                .claim("name", user.name())
                .claim("role", user.role().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expireHours * 3600)))
                .signWith(key)
                .compact();
    }

    public AuthUser parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return new AuthUser(
                    claims.getSubject(),
                    claims.get("username", String.class),
                    claims.get("name", String.class),
                    Role.valueOf(claims.get("role", String.class))
            );
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "登录已失效，请重新登录");
        }
    }

    private static String pad(String secret) {
        if (secret.length() >= 32) {
            return secret;
        }
        return secret + "0".repeat(32 - secret.length());
    }
}
