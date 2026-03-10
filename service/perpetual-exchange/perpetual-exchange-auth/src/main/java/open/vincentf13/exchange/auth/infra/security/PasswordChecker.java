package open.vincentf13.exchange.auth.infra.security;

import open.vincentf13.sdk.auth.jwt.OpenJwtLoginUserDetails;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

public class PasswordChecker extends DaoAuthenticationProvider {

  public PasswordChecker(PasswordEncoder passwordEncoder) {
    setPasswordEncoder(passwordEncoder);
  }

  @Override
  protected void additionalAuthenticationChecks(
      UserDetails userDetails, UsernamePasswordAuthenticationToken authentication)
      throws BadCredentialsException {
    if (authentication.getCredentials() == null) {
      throw new BadCredentialsException(
          messages.getMessage(
              "AbstractUserDetailsAuthenticationProvider.badCredentials", "Bad credentials"));
    }

    if (!(userDetails instanceof OpenJwtLoginUserDetails openJwtLoginUserDetails)) {
      throw new BadCredentialsException("Unsupported user details type");
    }

    String presentedPassword = authentication.getCredentials().toString();
    String saltedPassword = presentedPassword + ':' + openJwtLoginUserDetails.getSalt();

    if (!getPasswordEncoder().matches(saltedPassword, openJwtLoginUserDetails.getPassword())) {
      throw new BadCredentialsException(
          messages.getMessage(
              "AbstractUserDetailsAuthenticationProvider.badCredentials", "Bad credentials"));
    }
  }
}
