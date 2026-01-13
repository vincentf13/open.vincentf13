package open.vincentf13.sdk.auth.server;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import open.vincentf13.sdk.auth.jwt.session.JwtSession;
import open.vincentf13.sdk.auth.jwt.session.JwtSessionStore;
import open.vincentf13.sdk.auth.jwt.token.JwtToken;
import open.vincentf13.sdk.auth.jwt.token.OpenJwtService;
import open.vincentf13.sdk.auth.jwt.token.OpenJwtService.GenerateTokenInfo;
import open.vincentf13.sdk.auth.jwt.token.RefreshToken;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

@Service
public class OpenJwtSessionService {

  private final OpenJwtService openJwtService;
  private final JwtSessionStore sessionStore;

  public OpenJwtSessionService(OpenJwtService openJwtService, JwtSessionStore sessionStore) {
    this.openJwtService = openJwtService;
    this.sessionStore = sessionStore;
  }

  public IssueResult issue(Authentication authentication) {
    String sessionId = UUID.randomUUID().toString();
    GenerateTokenInfo accessToken = openJwtService.generateAccessToken(sessionId, authentication);
    GenerateTokenInfo refreshToken =
        openJwtService.generateRefreshToken(sessionId, authentication.getName());
    List<String> authorities =
        authentication.getAuthorities().stream().map(granted -> granted.getAuthority()).toList();
    JwtSession session =
        new JwtSession(
            sessionId,
            authentication.getName(),
            accessToken.issuedAt(),
            refreshToken.expiresAt(),
            authorities);
    sessionStore.save(session);
    OpenLog.info(
        AuthServerEvent.JWT_SESSION_CREATED,
        "sessionId",
        sessionId,
        "username",
        authentication.getName());
    return new IssueResult(sessionId, authentication.getName(), accessToken, refreshToken);
  }

  public Optional<IssueResult> refresh(String refreshTokenValue) {
    Optional<RefreshToken> refreshToken = openJwtService.parseRefreshToken(refreshTokenValue);
    if (refreshToken.isEmpty()) {
      return Optional.empty();
    }
    RefreshToken claims = refreshToken.get();
    String sessionId = claims.sessionId();
    if (sessionId == null) {
      OpenLog.warn(AuthServerEvent.REFRESH_MISSING_SESSION, "subject", claims.subject());
      return Optional.empty();
    }
    Optional<JwtSession> sessionOpt = sessionStore.findById(sessionId);
    Instant now = Instant.now();
    if (sessionOpt.isEmpty()) {
      OpenLog.info(AuthServerEvent.REFRESH_SESSION_NOT_FOUND, "sessionId", sessionId);
      return Optional.empty();
    }
    JwtSession session = sessionOpt.get();
    if (!session.getUsername().equals(claims.subject())) {
      OpenLog.warn(
          AuthServerEvent.REFRESH_SUBJECT_MISMATCH,
          "sessionId",
          sessionId,
          "tokenSubject",
          claims.subject(),
          "storedUsername",
          session.getUsername());
      return Optional.empty();
    }
    if (!session.isActive(now)) {
      OpenLog.info(AuthServerEvent.REFRESH_SESSION_INACTIVE, "sessionId", sessionId);
      return Optional.empty();
    }
    GenerateTokenInfo newAccess =
        openJwtService.generateAccessToken(
            sessionId, buildAuthentication(session, refreshTokenValue));
    GenerateTokenInfo newRefresh =
        openJwtService.generateRefreshToken(sessionId, session.getUsername());
    session.setRefreshTokenExpiresAt(newRefresh.expiresAt());
    sessionStore.save(session);
    OpenLog.info(
        AuthServerEvent.JWT_SESSION_REFRESHED,
        "sessionId",
        sessionId,
        "username",
        session.getUsername());
    return Optional.of(new IssueResult(sessionId, session.getUsername(), newAccess, newRefresh));
  }

  public void revoke(String sessionId, String reason) {
    sessionStore.markRevoked(sessionId, Instant.now(), reason);
    sessionStore.delete(sessionId);
    OpenLog.info(AuthServerEvent.JWT_SESSION_REVOKED, "sessionId", sessionId, "reason", reason);
  }

  private Authentication buildAuthentication(JwtSession session, String refreshTokenValue) {
    return new JwtToken(
        session.getUsername(),
        refreshTokenValue,
        session.getAuthorities().stream().map(SimpleGrantedAuthority::new).toList(),
        session.getId(),
        Instant.now(),
        session.getRefreshTokenExpiresAt());
  }

  public record IssueResult(
      String sessionId,
      String username,
      GenerateTokenInfo accessToken,
      GenerateTokenInfo refreshToken) {}
}
