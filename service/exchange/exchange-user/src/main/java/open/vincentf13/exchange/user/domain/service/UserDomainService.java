package open.vincentf13.exchange.user.domain.service;

import open.vincentf13.exchange.user.domain.model.UserAggregate;
import open.vincentf13.exchange.user.domain.model.UserStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Component
public class UserDomainService {

    public UserAggregate createActiveUser(String rawEmail, String externalId) {
        String normalizedEmail = normalizeEmail(rawEmail);
        Instant now = Instant.now();
        return UserAggregate.builder()
                .email(normalizedEmail)
                .externalId(externalId != null ? externalId : UUID.randomUUID().toString())
                .status(UserStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public String normalizeEmail(String rawEmail) {
        Objects.requireNonNull(rawEmail, "email must not be null");
        return rawEmail.toLowerCase();
    }
}
