package open.vincentf13.sdk.auth.jwt;

import open.vincentf13.sdk.auth.jwt.config.JwtProperties;
import open.vincentf13.sdk.auth.jwt.model.JwtParseInfo;
import open.vincentf13.sdk.auth.jwt.model.RefreshTokenParseInfo;
import open.vincentf13.sdk.core.OpenLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class OpenJwtService {

    private static final Logger log = LoggerFactory.getLogger(OpenJwtService.class);
    public static final String AUTHORITIES_CLAIM = "authorities";
    public static final String SESSION_ID_CLAIM = "sid";
    public static final String TOKEN_TYPE_CLAIM = "token_type";
    public static final String USER_ID_CLAIM = "uid";
    public static final String EMAIL_CLAIM = "email";

    private final JwtProperties properties;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    public OpenJwtService(JwtProperties properties, ObjectProvider<JwtEncoder> encoderProvider,
                          ObjectProvider<JwtDecoder> decoderProvider) {
        this.properties = properties;
        this.jwtEncoder = encoderProvider.getIfAvailable();
        this.jwtDecoder = decoderProvider.getIfAvailable();
    }

    public GenerateTokenInfo generateAccessToken(String sessionId, Authentication authentication) {
        JwtEncoder encoder = requireEncoder();
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(properties.getAccessTokenTtlSeconds());

        Long userId = null;
        String email = null;
        if (authentication.getPrincipal() instanceof OpenJwtLoginUser user) {
            userId = user.getUserId();
            email = user.getUsername();
        }

        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(authentication.getName())
                .claim(TOKEN_TYPE_CLAIM, TokenType.ACCESS.name())
                .claim(AUTHORITIES_CLAIM, authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toList()));

        if (userId != null) {
            builder.claim(USER_ID_CLAIM, userId);
        }
        if (email != null) {
            builder.claim(EMAIL_CLAIM, email);
        }
        if (sessionId != null) {
            builder.claim(SESSION_ID_CLAIM, sessionId);
        }

        JwsHeader headers = JwsHeader.with(MacAlgorithm.HS256).build();
        String tokenValue = encoder.encode(JwtEncoderParameters.from(headers, builder.build())).getTokenValue();
        OpenLog.debug(log,
                "JwtAccessIssued",
                () -> "Access jwtToken issued",
                "subject", authentication.getName(),
                "userId", userId,
                "email", email,
                "sessionId", sessionId == null ? "<legacy>" : sessionId);
        return new GenerateTokenInfo(tokenValue, issuedAt, expiresAt, TokenType.ACCESS, sessionId);
    }

    public GenerateTokenInfo generateRefreshToken(String sessionId, String subject) {
        JwtEncoder encoder = requireEncoder();
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

        JwsHeader headers = JwsHeader.with(MacAlgorithm.HS256).build();
        String tokenValue = encoder.encode(JwtEncoderParameters.from(headers, builder.build())).getTokenValue();
        OpenLog.debug(log,
                "JwtRefreshIssued",
                () -> "Refresh jwtToken issued",
                "subject", subject,
                "sessionId", sessionId == null ? "<legacy>" : sessionId);
        return new GenerateTokenInfo(tokenValue, issuedAt, expiresAt, TokenType.REFRESH, sessionId);
    }

    public Optional<JwtParseInfo> parseAccessToken(String tokenValue) {
        try {
            Jwt jwt = jwtDecoder.decode(tokenValue);
            TokenType tokenType = resolveTokenType(jwt);
            if (tokenType != TokenType.ACCESS) {
                OpenLog.warn(log, "JwtInvalidType", "Token is not an access jwtToken", "expected", TokenType.ACCESS, "actual", tokenType);
                return Optional.empty();
            }

            // Extract user information from JWT claims
            Long userId = jwt.getClaim(USER_ID_CLAIM);
            String email = jwt.getClaimAsString(EMAIL_CLAIM);
            List<String> authorities = jwt.getClaimAsStringList(AUTHORITIES_CLAIM);
            List<SimpleGrantedAuthority> granted = authorities == null ? Collections.emptyList() : authorities.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
            String sessionId = jwt.getClaimAsString(SESSION_ID_CLAIM);

            // Construct OpenJwtUser from JWT claims
            OpenJwtLoginUser user = new OpenJwtLoginUser(userId, email, granted);
            JwtParseInfo authentication = new JwtParseInfo(user, tokenValue, granted, sessionId, jwt.getIssuedAt(), jwt.getExpiresAt());
            return Optional.of(authentication);
        } catch (JwtException ex) {
            OpenLog.warn(log, "JwtInvalid", "JWT validation failed", ex, "reason", ex.getMessage());
            return Optional.empty();
        }
    }

    public Optional<RefreshTokenParseInfo> parseRefreshToken(String tokenValue) {
        try {
            Jwt jwt = jwtDecoder.decode(tokenValue);
            TokenType tokenType = resolveTokenType(jwt);
            if (tokenType != TokenType.REFRESH) {
                OpenLog.warn(log, "JwtInvalidType", "Token is not a refresh jwtToken", "expected", TokenType.REFRESH, "actual", tokenType);
                return Optional.empty();
            }
            String sessionId = jwt.getClaimAsString(SESSION_ID_CLAIM);
            return Optional.of(new RefreshTokenParseInfo(jwt.getTokenValue(), jwt.getSubject(), sessionId, jwt.getIssuedAt(), jwt.getExpiresAt()));
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
            OpenLog.warn(log, "JwtUnknownType", "Unknown jwtToken type", ex, "value", raw, "tokenId", jwt.getId() == null ? UUID.randomUUID() : jwt.getId());
            return TokenType.ACCESS;
        }
    }

    private JwtEncoder requireEncoder() {
        if (jwtEncoder == null) {
            throw new IllegalStateException("JwtEncoder is not available for jwtToken generation");
        }
        return jwtEncoder;
    }

    public JwtEncoder getJwtEncoder() {
        return jwtEncoder;
    }

    public JwtProperties getProperties() {
        return properties;
    }

    public enum TokenType {
        ACCESS,
        REFRESH
    }

    public record GenerateTokenInfo(String token, Instant issuedAt, Instant expiresAt, TokenType tokenType, String sessionId) { }
}
