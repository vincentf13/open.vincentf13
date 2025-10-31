package open.vincentf13.exchange.user.mapper;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.user.domain.User;
import open.vincentf13.exchange.user.dto.UserResponse;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UserDtoMapper {

    public static UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getExternalId(),
                user.getEmail(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
