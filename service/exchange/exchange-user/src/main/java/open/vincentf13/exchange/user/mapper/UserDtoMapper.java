package open.vincentf13.exchange.user.mapper;

import open.vincentf13.exchange.user.domain.model.User;
import open.vincentf13.exchange.user.dto.UserResponse;

public final class UserDtoMapper {

    private UserDtoMapper() {
    }

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
