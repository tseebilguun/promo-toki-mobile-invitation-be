package mn.unitel.campaign;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.Map;

@ApplicationScoped
public class JwtService {
    private static final Logger logger = Logger.getLogger(JwtService.class);

    @ConfigProperty(name = "jwt.key")
    String SECRET_KEY;

    private Key hmacKey;

    @PostConstruct
    void init() {
        byte[] keyBytes = SECRET_KEY.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            logger.warn("jwt.key appears shorter than 32 bytes. Consider a longer random value for HS256.");
        }
        hmacKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateTokenWithPhone(String subject, String msisdn, String tokiId) {
        Map<String, Object> claims = Map.of(
                "msisdn", msisdn,
                "tokiId", tokiId
        );

        long nowMillis = System.currentTimeMillis();
        long expMillis = nowMillis + 15 * 60 * 1000; // 15 min

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(nowMillis))
                .setExpiration(new Date(expMillis))
                .signWith(hmacKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean isExpiredOrInvalid(String token) {
        try {
            Date exp = Jwts.parserBuilder()
                    .setSigningKey(hmacKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getExpiration();
            return exp.before(new Date());
        } catch (JwtException e) {
            return true;
        }
    }

    public String extractSubject(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(hmacKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return claims.getSubject();
        } catch (JwtException e) {
            return null;
        }
    }

    public String getStringClaim(String token, String key) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(hmacKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return claims.get(key, String.class);
        } catch (JwtException e) {
            return null;
        }
    }
}