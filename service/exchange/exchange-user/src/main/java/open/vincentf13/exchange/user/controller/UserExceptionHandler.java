package open.vincentf13.exchange.user.controller;

import open.vincentf13.common.spring.mvc.OpenApiResponse;
import open.vincentf13.exchange.user.exception.UserAlreadyExistsException;
import open.vincentf13.exchange.user.exception.UserNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class UserExceptionHandler {

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<OpenApiResponse<Void>> handleUserExists(UserAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(OpenApiResponse.failure("USER_EXISTS", ex.getMessage()));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<OpenApiResponse<Void>> handleNotFound(UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(OpenApiResponse.failure("USER_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<OpenApiResponse<Void>> handleValidation(Exception ex) {
        return ResponseEntity.badRequest()
                .body(OpenApiResponse.failure("VALIDATION_ERROR", ex.getMessage()));
    }
}
