package open.vincentf13.common.sdk.spring.security.token;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.crypto.spec.SecretKeySpec;
import open.vincentf13.common.core.log.OpenLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

/**
 * Lightweight wrapper around Nimbus JWT encoder/decoder so SDK consumers can issue and validate
 * HMAC-signed access tokens without wiring the auth server module.
 */
@Service
public class OpenJwtToken {

    private static final Logger log = LoggerFactory.getLogger(OpenJwtToken.class);
    private static final String AUTHORITIES_CLAIM = "authorities";

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

    /** Build a signed JWT for the authenticated principal including authority claims. */
    public TokenDetails generate(Authentication authentication) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(properties.getAccessTokenTtlSeconds());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(authentication.getName())
                // 權限
                .claim(AUTHORITIES_CLAIM, authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList())
                .build();
        String tokenValue = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        OpenLog.debug(log, "JwtIssued", () -> "JWT issued", "subject", authentication.getName());
        return new TokenDetails(tokenValue, issuedAt, expiresAt);
    }

    /** Decode a bearer token and recover the {@link Authentication} if the signature is valid. */
    public Optional<Authentication> parse(String tokenValue) {
        try {
            // Nimbus 預設會驗簽並檢查 exp/nbf，所以基本的過期驗證已經在這裡完成
            Jwt jwt = jwtDecoder.decode(tokenValue);
            String subject = jwt.getSubject();
            // 權限
            List<String> authorities = jwt.getClaimAsStringList(AUTHORITIES_CLAIM);
            Collection<GrantedAuthority> granted = authorities == null ? List.of() : authorities.stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();
            Authentication authentication = new UsernamePasswordAuthenticationToken(subject, tokenValue, granted);
            return Optional.of(authentication);
        } catch (JwtException ex) {
            OpenLog.warn(log, "JwtInvalid", "JWT validation failed", ex, "reason", ex.getMessage());
            return Optional.empty();
        }
    }

    public record TokenDetails(String token, Instant issuedAt, Instant expiresAt) { }
}
