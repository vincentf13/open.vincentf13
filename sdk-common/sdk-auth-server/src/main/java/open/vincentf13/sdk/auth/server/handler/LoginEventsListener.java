package open.vincentf13.sdk.auth.server.handler;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

@Component
public class LoginEventsListener {
  @EventListener
  public void onFailure(AuthenticationFailureBadCredentialsEvent e){ /* 累計失敗 */ }
  @EventListener
  public void onLocked(AuthenticationFailureLockedEvent e){ /* 被鎖定 */ }
  @EventListener
  public void onSuccess(AuthenticationSuccessEvent e){ /* 清空失敗次數 */ }
}