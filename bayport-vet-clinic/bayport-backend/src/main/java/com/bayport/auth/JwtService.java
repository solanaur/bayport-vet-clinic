package com.bayport.auth;

import com.bayport.security.SecretRequirements;
import com.bayport.security.TokenScopes;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {

    public static final long MFA_TOKEN_MS = 5L * 60L * 1000L;
    public static final long MFA_SETUP_TOKEN_MS = 10L * 60L * 1000L;

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${jwt.secret:}") String secret,
            @Value("${jwt.expiration:28800000}") long expirationMs) {
        byte[] keyBytes = SecretRequirements.requireAtLeast32Bytes(secret, "JWT_SECRET");
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
    }

    public String generateAccessToken(String username, int tokenVersion) {
        return build(username, TokenScopes.ACCESS, expirationMs, tokenVersion);
    }

    public String generateMfaToken(String username) {
        return build(username, TokenScopes.MFA, MFA_TOKEN_MS, null);
    }

    public String generateMfaSetupToken(String username) {
        return build(username, TokenScopes.MFA_SETUP, MFA_SETUP_TOKEN_MS, null);
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractScope(String token) {
        return extractClaim(token, claims -> claims.get("scope", String.class));
    }

    public Integer extractTokenVersion(String token) {
        Object value = extractClaim(token, claims -> claims.get("tv"));
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public boolean isAccessTokenValid(String token, String username, int currentVersion) {
        if (username == null || !username.equals(extractUsername(token))) {
            return false;
        }
        if (isTokenExpired(token)) {
            return false;
        }
        if (!TokenScopes.ACCESS.equals(extractScope(token))) {
            return false;
        }
        Integer version = extractTokenVersion(token);
        return version != null && version == currentVersion;
    }

    public boolean isScopedTokenValid(String token, String expectedScope) {
        if (isTokenExpired(token)) {
            return false;
        }
        String username = extractUsername(token);
        return username != null && !username.isBlank() && expectedScope.equals(extractScope(token));
    }

    private String build(String username, String scope, long ttlMs, Integer tokenVersion) {
        var builder = Jwts.builder()
                .subject(username)
                .claim("scope", scope)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ttlMs));
        if (tokenVersion != null) {
            builder.claim("tv", tokenVersion);
        }
        return builder.signWith(signingKey).compact();
    }

    private Claims extractAllClaims(String token) {
        JwtParser parser = Jwts.parser()
                .verifyWith(signingKey)
                .build();
        return parser.parseSignedClaims(token).getPayload();
    }
}
