package open.vincentf13.sdk.auth.server.service;

import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.sdk.auth.server.event.AuthServerEventEnum;
import open.vincentf13.sdk.auth.jwt.session.JwtSession;
import open.vincentf13.sdk.auth.jwt.session.JwtSessionStore;
import open.vincentf13.sdk.auth.jwt.OpenJwtService;
import open.vincentf13.sdk.auth.jwt.OpenJwtService.GenerateTokenInfo;
import open.vincentf13.sdk.auth.jwt.model.JwtParseInfo;
import open.vincentf13.sdk.auth.jwt.model.RefreshTokenParseInfo;
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
public class OpenJwtSessionService {

    private static final Logger log = LoggerFactory.getLogger(OpenJwtSessionService.class);

    private final OpenJwtService openJwtService;
    private final JwtSessionStore sessionStore;

    public OpenJwtSessionService(OpenJwtService openJwtService,
                                 JwtSessionStore sessionStore) {
        this.openJwtService = openJwtService;
        this.sessionStore = sessionStore;
    }

    public IssueResult issue(Authentication authentication) {
        String sessionId = UUID.randomUUID().toString();
        GenerateTokenInfo accessToken = openJwtService.generateAccessToken(sessionId, authentication);
        GenerateTokenInfo refreshToken = openJwtService.generateRefreshToken(sessionId, authentication.getName());
        List<String> authorities = authentication.getAuthorities().stream()
                .map(granted -> granted.getAuthority())
                .toList();
        JwtSession session = new JwtSession(sessionId, authentication.getName(), accessToken.issuedAt(), refreshToken.expiresAt(), authorities);
        sessionStore.save(session);
        OpenLog.info(log, AuthServerEventEnum.JWT_SESSION_CREATED,
                "sessionId", sessionId,
                "username", authentication.getName());
        return new IssueResult(sessionId, authentication.getName(), accessToken, refreshToken);
    }

    public Optional<IssueResult> refresh(String refreshTokenValue) {
        Optional<RefreshTokenParseInfo> refreshToken = openJwtService.parseRefreshToken(refreshTokenValue);
        if (refreshToken.isEmpty()) {
            return Optional.empty();
        }
        RefreshTokenParseInfo claims = refreshToken.get();
        String sessionId = claims.sessionId();
        if (sessionId == null) {
            OpenLog.warn(log, AuthServerEventEnum.REFRESH_MISSING_SESSION, "subject", claims.subject());
            return Optional.empty();
        }
        Optional<JwtSession> sessionOpt = sessionStore.findById(sessionId);
        Instant now = Instant.now();
        if (sessionOpt.isEmpty()) {
            OpenLog.info(log, AuthServerEventEnum.REFRESH_SESSION_NOT_FOUND, "sessionId", sessionId);
            return Optional.empty();
        }
        JwtSession session = sessionOpt.get();
        if (!session.getUsername().equals(claims.subject())) {
            OpenLog.warn(log, AuthServerEventEnum.REFRESH_SUBJECT_MISMATCH,
                    "sessionId", sessionId,
                    "tokenSubject", claims.subject(),
                    "storedUsername", session.getUsername());
            return Optional.empty();
        }
        if (!session.isActive(now)) {
            OpenLog.info(log, AuthServerEventEnum.REFRESH_SESSION_INACTIVE, "sessionId", sessionId);
            return Optional.empty();
        }
        GenerateTokenInfo newAccess = openJwtService.generateAccessToken(sessionId, buildAuthentication(session, refreshTokenValue));
        GenerateTokenInfo newRefresh = openJwtService.generateRefreshToken(sessionId, session.getUsername());
        session.setRefreshTokenExpiresAt(newRefresh.expiresAt());
        sessionStore.save(session);
        OpenLog.info(log, AuthServerEventEnum.JWT_SESSION_REFRESHED,
                "sessionId", sessionId,
                "username", session.getUsername());
        return Optional.of(new IssueResult(sessionId, session.getUsername(), newAccess, newRefresh));
    }

    public void revoke(String sessionId, String reason) {
        sessionStore.markRevoked(sessionId, Instant.now(), reason);
        sessionStore.delete(sessionId);
        OpenLog.info(log, AuthServerEventEnum.JWT_SESSION_REVOKED,
                "sessionId", sessionId,
                "reason", reason);
    }

    private Authentication buildAuthentication(JwtSession session, String refreshTokenValue) {
        return new JwtParseInfo(
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
                              GenerateTokenInfo accessToken,
                              GenerateTokenInfo refreshToken) { }
}
