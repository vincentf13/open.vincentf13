package open.vincentf13.sdk.auth.server.token;

import open.vincentf13.sdk.auth.jwt.user.OpenJwtUser;
import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.sdk.auth.jwt.token.OpenJwt;
import open.vincentf13.sdk.auth.jwt.token.OpenJwt.TokenDetails;
import open.vincentf13.sdk.auth.jwt.token.OpenJwt.TokenType;
import open.vincentf13.sdk.auth.jwt.token.model.JwtAuthenticationToken;
import open.vincentf13.sdk.auth.jwt.token.model.RefreshTokenClaims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.time.Instant;
import java.util.stream.Collectors;

@Component
public class OpenJwtGenerate {

    private static final Logger log = LoggerFactory.getLogger(OpenJwtGenerate.class);
    private final OpenJwt openJwt;

    public OpenJwtGenerate(OpenJwt openJwt) {
        this.openJwt = openJwt;
    }

    public TokenDetails generateAccessToken(String sessionId, Authentication authentication) {
        JwtEncoder encoder = openJwt.getJwtEncoder();
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(openJwt.getProperties().getAccessTokenTtlSeconds());

        // Extract user information from principal
        Long userId = null;
        String email = null;
        if (authentication.getPrincipal() instanceof OpenJwtUser user) {
            userId = user.getUserId();
            email = user.getUsername(); // getUsername returns email
        }

        // @formatter:off
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuer(openJwt.getProperties().getIssuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(authentication.getName())
                .claim(OpenJwt.TOKEN_TYPE_CLAIM, TokenType.ACCESS.name())
                .claim(OpenJwt.AUTHORITIES_CLAIM, authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toList()));
        // @formatter:on

        // Add user information claims
        if (userId != null) {
            builder.claim(OpenJwt.USER_ID_CLAIM, userId);
        }
        if (email != null) {
            builder.claim(OpenJwt.EMAIL_CLAIM, email);
        }

        if (sessionId != null) {
            builder.claim(OpenJwt.SESSION_ID_CLAIM, sessionId);
        }
        JwsHeader headers = JwsHeader.with(MacAlgorithm.HS256).build();
        String tokenValue = encoder.encode(JwtEncoderParameters.from(headers, builder.build())).getTokenValue();
        OpenLog.debug(log,
                "JwtAccessIssued",
                () -> "Access token issued",
                "subject", authentication.getName(),
                "userId", userId,
                "email", email,
                "sessionId", sessionId == null ? "<legacy>" : sessionId);
        return new TokenDetails(tokenValue, issuedAt, expiresAt, TokenType.ACCESS, sessionId);
    }

    public TokenDetails generateRefreshToken(String sessionId, String subject) {
        JwtEncoder encoder = openJwt.getJwtEncoder();
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(openJwt.getProperties().getRefreshTokenTtlSeconds());
        // @formatter:off
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuer(openJwt.getProperties().getIssuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(subject)
                .claim(OpenJwt.TOKEN_TYPE_CLAIM, TokenType.REFRESH.name());
        // @formatter:on
        if (sessionId != null) {
            builder.claim(OpenJwt.SESSION_ID_CLAIM, sessionId);
        }
        JwsHeader headers = JwsHeader.with(MacAlgorithm.HS256).build();
        String tokenValue = encoder.encode(JwtEncoderParameters.from(headers, builder.build())).getTokenValue();
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
        return openJwt.parseAccessToken(tokenValue);
    }

    public Optional<RefreshTokenClaims> parseRefreshToken(String tokenValue) {
        return openJwt.parseRefreshToken(tokenValue);
    }

}
