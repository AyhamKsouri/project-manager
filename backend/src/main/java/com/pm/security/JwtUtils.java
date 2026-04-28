package com.pm.security;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtils {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    @Value("${pm.app.jwtSecret:DefaultSecretKeyForDevOnlyPleaseReplaceInProd123456}")
    private String jwtSecret;

    @PostConstruct
    public void init() {
        logger.info("JwtUtils initialized with secret length: {}", jwtSecret != null ? jwtSecret.length() : "null");
        if (jwtSecret != null && jwtSecret.length() < 32) {
            logger.error("FATAL: JWT Secret is too short! It must be at least 32 characters.");
        }
    }
    private final long jwtExpirationMs = 86400000;

    private SecretKey getSigningKey() {
        if (jwtSecret == null || jwtSecret.length() < 32) {
            logger.warn("JWT Secret is too short or null! Using fallback for development.");
            return Keys.hmacShaKeyFor("FallbackSecretKeyForDevOnlyPleaseReplaceInProd123456".getBytes(StandardCharsets.UTF_8));
        }
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateJwtToken(Authentication authentication) {
        UserDetailsImpl userPrincipal = (UserDetailsImpl) authentication.getPrincipal();
        logger.info("Generating token for user: {}", userPrincipal.getUsername());
        String token = Jwts.builder()
                .subject(userPrincipal.getUsername())
                .issuedAt(new Date())
                .expiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(getSigningKey())
                .compact();
        logger.info("Token generated successfully ({} bytes)", token.length());
        return token;
    }

    public String getUserNameFromJwtToken(String token) {
        return Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload().getSubject();
    }

    public boolean validateJwtToken(String authToken) {
        try {
            logger.info("Validating JWT token...");
            Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(authToken);
            logger.info("JWT token is valid");
            return true;
        } catch (MalformedJwtException e) {
            logger.error("Invalid JWT token format: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            logger.error("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.error("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("JWT claims string is empty: {}", e.getMessage());
        } catch (SignatureException e) {
            logger.error("Invalid JWT signature: {}. This usually means the secret key used to sign the token is different from the one used to verify it.", e.getMessage());
        } catch (Exception e) {
            logger.error("JWT validation error ({}): {}", e.getClass().getSimpleName(), e.getMessage());
        }
        return false;
    }
}
