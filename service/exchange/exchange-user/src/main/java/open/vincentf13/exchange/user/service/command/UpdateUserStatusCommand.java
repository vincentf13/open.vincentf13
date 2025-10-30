package open.vincentf13.exchange.user.service.command;

import open.vincentf13.exchange.user.domain.model.UserStatus;

public record UpdateUserStatusCommand(Long userId,
                                      UserStatus status) {
}
