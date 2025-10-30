package open.vincentf13.exchange.user.exception;

public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException(String email) {
        super("Email already registered: " + email);
    }
}
