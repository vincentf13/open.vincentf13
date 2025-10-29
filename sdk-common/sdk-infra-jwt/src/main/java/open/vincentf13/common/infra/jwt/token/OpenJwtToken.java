package open.vincentf13.common.infra.jwt.token;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import open.vincentf13.common.core.log.OpenLog;
import open.vincentf13.common.infra.jwt.token.model.JwtAuthenticationToken;
import open.vincentf13.common.infra.jwt.token.model.RefreshTokenClaims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OpenJwtToken {

    private static final Logger log = LoggerFactory.getLogger(OpenJwtToken.class);
    private static final String AUTHORITIES_CLAIM = "authorities";
    private static final String SESSION_ID_CLAIM = "sid";
    private static final String TOKEN_TYPE_CLAIM = "token_type";

    private final JwtProperties properties;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    public OpenJwtToken(JwtProperties properties) {
        this.properties = properties;
        byte[] secret = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        SecretKeySpec secretKey = new SecretKeySpec(secret, "HmacSHA256");
        this.jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
        this.jwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey).build();
    }

    public TokenDetails generateAccessToken(String sessionId, Authentication authentication) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(properties.getAccessTokenTtlSeconds());
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(authentication.getName())
                .claim(TOKEN_TYPE_CLAIM, TokenType.ACCESS.name());
        if (sessionId != null) {
            builder.claim(SESSION_ID_CLAIM, sessionId);
        }
        builder.claim(AUTHORITIES_CLAIM, authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList()));
        JwtClaimsSet claims = builder.build();
        String tokenValue = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        OpenLog.debug(log,
                "JwtAccessIssued",
                () -> "Access token issued",
                "subject", authentication.getName(),
                "sessionId", sessionId == null ? "<legacy>" : sessionId);
        return new TokenDetails(tokenValue, issuedAt, expiresAt, TokenType.ACCESS, sessionId);
    }

    public TokenDetails generateRefreshToken(String sessionId, String subject) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(properties.getRefreshTokenTtlSeconds());
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(subject)
                .claim(TOKEN_TYPE_CLAIM, TokenType.REFRESH.name());
        if (sessionId != null) {
            builder.claim(SESSION_ID_CLAIM, sessionId);
        }
        String tokenValue = jwtEncoder.encode(JwtEncoderParameters.from(builder.build())).getTokenValue();
        OpenLog.debug(log,
                "JwtRefreshIssued",
                () -> "Refresh token issued",
                "subject", subject,
                "sessionId", sessionId == null ? "<legacy>" : sessionId);
        return new TokenDetails(tokenValue, issuedAt, expiresAt, TokenType.REFRESH, sessionId);
    }

    @Deprecated(forRemoval = false)
    public TokenDetails generate(Authentication authentication) {
        return generateAccessToken(null, authentication);
    }

    public Optional<JwtAuthenticationToken> parseAccessToken(String tokenValue) {
        try {
            Jwt jwt = jwtDecoder.decode(tokenValue);
            TokenType tokenType = resolveTokenType(jwt);
            if (tokenType != TokenType.ACCESS) {
                OpenLog.warn(log, "JwtInvalidType", "Token is not an access token", "expected", TokenType.ACCESS, "actual", tokenType);
                return Optional.empty();
            }
            String subject = jwt.getSubject();
            List<String> authorities = jwt.getClaimAsStringList(AUTHORITIES_CLAIM);
            List<SimpleGrantedAuthority> granted = authorities == null ? Collections.emptyList() : authorities.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
            String sessionId = jwt.getClaimAsString(SESSION_ID_CLAIM);
            JwtAuthenticationToken authentication = new JwtAuthenticationToken(subject, tokenValue, granted, sessionId, jwt.getIssuedAt(), jwt.getExpiresAt());
            return Optional.of(authentication);
        } catch (JwtException ex) {
            OpenLog.warn(log, "JwtInvalid", "JWT validation failed", ex, "reason", ex.getMessage());
            return Optional.empty();
        }
    }

    public Optional<RefreshTokenClaims> parseRefreshToken(String tokenValue) {
        try {
            Jwt jwt = jwtDecoder.decode(tokenValue);
            TokenType tokenType = resolveTokenType(jwt);
            if (tokenType != TokenType.REFRESH) {
                OpenLog.warn(log, "JwtInvalidType", "Token is not a refresh token", "expected", TokenType.REFRESH, "actual", tokenType);
                return Optional.empty();
            }
            String sessionId = jwt.getClaimAsString(SESSION_ID_CLAIM);
            return Optional.of(new RefreshTokenClaims(jwt.getTokenValue(), jwt.getSubject(), sessionId, jwt.getIssuedAt(), jwt.getExpiresAt()));
        } catch (JwtException ex) {
            OpenLog.warn(log, "JwtInvalid", "JWT validation failed", ex, "reason", ex.getMessage());
            return Optional.empty();
        }
    }

    private TokenType resolveTokenType(Jwt jwt) {
        String raw = jwt.getClaimAsString(TOKEN_TYPE_CLAIM);
        if (raw == null) {
            return TokenType.ACCESS;
        }
        try {
            return TokenType.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            OpenLog.warn(log, "JwtUnknownType", "Unknown token type", ex, "value", raw, "tokenId", jwt.getId() == null ? UUID.randomUUID() : jwt.getId());
            return TokenType.ACCESS;
        }
    }

    public enum TokenType {
        ACCESS,
        REFRESH
    }

    public record TokenDetails(String token, Instant issuedAt, Instant expiresAt, TokenType tokenType, String sessionId) { }
}
