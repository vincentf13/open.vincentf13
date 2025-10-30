package open.vincentf13.exchange.user.service.command;

public record RegisterUserCommand(String email,
                                  String password,
                                  String externalId) {
}
