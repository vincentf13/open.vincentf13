package open.vincentf13.common.sdk.spring.security.service;

import open.vincentf13.common.core.log.OpenLog;
import open.vincentf13.common.infra.jwt.session.JwtSession;
import open.vincentf13.common.sdk.spring.security.store.AuthJwtSessionStore;
import open.vincentf13.common.infra.jwt.token.OpenJwtToken.TokenDetails;
import open.vincentf13.common.infra.jwt.token.model.JwtAuthenticationToken;
import open.vincentf13.common.infra.jwt.token.model.RefreshTokenClaims;
import open.vincentf13.common.sdk.spring.security.token.OpenJwtTokenGenerate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthJwtSessionService {

    private static final Logger log = LoggerFactory.getLogger(AuthJwtSessionService.class);

    private final OpenJwtTokenGenerate tokenGenerate;
    private final AuthJwtSessionStore sessionStore;

    public AuthJwtSessionService(OpenJwtTokenGenerate tokenGenerate,
                                 AuthJwtSessionStore sessionStore) {
        this.tokenGenerate = tokenGenerate;
        this.sessionStore = sessionStore;
    }

    public IssueResult issue(Authentication authentication) {
        String sessionId = UUID.randomUUID().toString();
        TokenDetails accessToken = tokenGenerate.generateAccessToken(sessionId, authentication);
        TokenDetails refreshToken = tokenGenerate.generateRefreshToken(sessionId, authentication.getName());
        List<String> authorities = authentication.getAuthorities().stream()
                .map(granted -> granted.getAuthority())
                .toList();
        JwtSession session = new JwtSession(sessionId, authentication.getName(), accessToken.issuedAt(), refreshToken.expiresAt(), authorities);
        sessionStore.save(session);
        OpenLog.info(log,
                "JwtSessionCreated",
                "Session created",
                "sessionId", sessionId,
                "username", authentication.getName());
        return new IssueResult(sessionId, authentication.getName(), accessToken, refreshToken);
    }

    public Optional<IssueResult> refresh(String refreshTokenValue) {
        Optional<RefreshTokenClaims> refreshToken = tokenGenerate.parseRefreshToken(refreshTokenValue);
        if (refreshToken.isEmpty()) {
            return Optional.empty();
        }
        RefreshTokenClaims claims = refreshToken.get();
        String sessionId = claims.sessionId();
        if (sessionId == null) {
            OpenLog.warn(log, "RefreshMissingSession", "Refresh token does not carry a session id", "subject", claims.subject());
            return Optional.empty();
        }
        Optional<JwtSession> sessionOpt = sessionStore.findById(sessionId);
        Instant now = Instant.now();
        if (sessionOpt.isEmpty()) {
            OpenLog.info(log, "RefreshSessionNotFound", "Unable to locate session for refresh", "sessionId", sessionId);
            return Optional.empty();
        }
        JwtSession session = sessionOpt.get();
        if (!session.getUsername().equals(claims.subject())) {
            OpenLog.warn(log,
                    "RefreshSubjectMismatch",
                    "Refresh token subject mismatch",
                    "sessionId", sessionId,
                    "tokenSubject", claims.subject(),
                    "storedUsername", session.getUsername());
            return Optional.empty();
        }
        if (!session.isActive(now)) {
            OpenLog.info(log, "RefreshSessionInactive", "Session already expired or revoked", "sessionId", sessionId);
            return Optional.empty();
        }
        TokenDetails newAccess = tokenGenerate.generateAccessToken(sessionId, buildAuthentication(session, refreshTokenValue));
        TokenDetails newRefresh = tokenGenerate.generateRefreshToken(sessionId, session.getUsername());
        session.setRefreshTokenExpiresAt(newRefresh.expiresAt());
        sessionStore.save(session);
        OpenLog.info(log,
                "JwtSessionRefreshed",
                "Session refreshed",
                "sessionId", sessionId,
                "username", session.getUsername());
        return Optional.of(new IssueResult(sessionId, session.getUsername(), newAccess, newRefresh));
    }

    public void revoke(String sessionId, String reason) {
        sessionStore.markRevoked(sessionId, Instant.now(), reason);
        sessionStore.delete(sessionId);
        OpenLog.info(log,
                "JwtSessionRevoked",
                "Session revoked",
                "sessionId", sessionId,
                "reason", reason);
    }

    private Authentication buildAuthentication(JwtSession session, String refreshTokenValue) {
        return new JwtAuthenticationToken(
                session.getUsername(),
                refreshTokenValue,
                session.getAuthorities().stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList(),
                session.getId(),
                Instant.now(),
                session.getRefreshTokenExpiresAt());
    }

    public record IssueResult(String sessionId,
                              String username,
                              TokenDetails accessToken,
                              TokenDetails refreshToken) { }
}
