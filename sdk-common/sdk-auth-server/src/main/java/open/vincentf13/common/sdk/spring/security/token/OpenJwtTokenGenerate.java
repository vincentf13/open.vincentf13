package open.vincentf13.common.sdk.spring.security.token;

import open.vincentf13.common.core.log.OpenLog;
import open.vincentf13.common.infra.jwt.token.OpenJwtToken;
import open.vincentf13.common.infra.jwt.token.OpenJwtToken.TokenDetails;
import open.vincentf13.common.infra.jwt.token.OpenJwtToken.TokenType;
import open.vincentf13.common.infra.jwt.token.model.JwtAuthenticationToken;
import open.vincentf13.common.infra.jwt.token.model.RefreshTokenClaims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.time.Instant;
import java.util.stream.Collectors;

@Component
public class OpenJwtTokenGenerate {

    private static final Logger log = LoggerFactory.getLogger(OpenJwtTokenGenerate.class);
    private final OpenJwtToken openJwtToken;

    public OpenJwtTokenGenerate(OpenJwtToken openJwtToken) {
        this.openJwtToken = openJwtToken;
    }

    public TokenDetails generateAccessToken(String sessionId, Authentication authentication) {
        JwtEncoder encoder = openJwtToken.getJwtEncoder();
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(openJwtToken.getProperties().getAccessTokenTtlSeconds());
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuer(openJwtToken.getProperties().getIssuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(authentication.getName())
                .claim(OpenJwtToken.TOKEN_TYPE_CLAIM, TokenType.ACCESS.name())
                .claim(OpenJwtToken.AUTHORITIES_CLAIM, authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toList()));
        if (sessionId != null) {
            builder.claim(OpenJwtToken.SESSION_ID_CLAIM, sessionId);
        }
        String tokenValue = encoder.encode(JwtEncoderParameters.from(builder.build())).getTokenValue();
        OpenLog.debug(log,
                "JwtAccessIssued",
                () -> "Access token issued",
                "subject", authentication.getName(),
                "sessionId", sessionId == null ? "<legacy>" : sessionId);
        return new TokenDetails(tokenValue, issuedAt, expiresAt, TokenType.ACCESS, sessionId);
    }

    public TokenDetails generateRefreshToken(String sessionId, String subject) {
        JwtEncoder encoder = openJwtToken.getJwtEncoder();
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(openJwtToken.getProperties().getRefreshTokenTtlSeconds());
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuer(openJwtToken.getProperties().getIssuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(subject)
                .claim(OpenJwtToken.TOKEN_TYPE_CLAIM, TokenType.REFRESH.name());
        if (sessionId != null) {
            builder.claim(OpenJwtToken.SESSION_ID_CLAIM, sessionId);
        }
        String tokenValue = encoder.encode(JwtEncoderParameters.from(builder.build())).getTokenValue();
        OpenLog.debug(log,
                "JwtRefreshIssued",
                () -> "Refresh token issued",
                "subject", subject,
                "sessionId", sessionId == null ? "<legacy>" : sessionId);
        return new TokenDetails(tokenValue, issuedAt, expiresAt, TokenType.REFRESH, sessionId);
    }

    public TokenDetails generate(Authentication authentication) {
        return generateAccessToken(null, authentication);
    }

    public Optional<JwtAuthenticationToken> parseAccessToken(String tokenValue) {
        return openJwtToken.parseAccessToken(tokenValue);
    }

    public Optional<RefreshTokenClaims> parseRefreshToken(String tokenValue) {
        return openJwtToken.parseRefreshToken(tokenValue);
    }

}
