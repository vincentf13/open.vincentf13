package open.vincentf13.sdk.auth.jwt.user;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

public class OpenJwtUser implements UserDetails {

    private final Long userId;
    private final String email;
    private final String secretHash;
    private final String salt;
    private final boolean accountNonLocked;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;

    public OpenJwtUser(Long userId,
                       String email,
                       String secretHash,
                       String salt,
                       boolean enabled,
                       boolean accountNonLocked,
                       Collection<? extends GrantedAuthority> authorities) {
        this.userId = userId;
        this.email = email;
        this.secretHash = secretHash;
        this.salt = salt;
        this.enabled = enabled;
        this.accountNonLocked = accountNonLocked;
        this.authorities = authorities == null ? Collections.emptyList() : authorities;
    }

    /**
     * Constructor for JWT-based authentication (no password credentials needed)
     */
    public OpenJwtUser(Long userId,
                       String email,
                       Collection<? extends GrantedAuthority> authorities) {
        this.userId = userId;
        this.email = email;
        this.secretHash = "";
        this.salt = "";
        this.enabled = true;
        this.accountNonLocked = true;
        this.authorities = authorities == null ? Collections.emptyList() : authorities;
    }

    public Long getUserId() {
        return userId;
    }

    public String getSalt() {
        return salt;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return secretHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
