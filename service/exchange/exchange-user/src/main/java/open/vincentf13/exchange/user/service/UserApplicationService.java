package open.vincentf13.exchange.user.service;

import open.vincentf13.exchange.user.domain.model.AuthCredential;
import open.vincentf13.exchange.user.domain.model.AuthCredentialType;
import open.vincentf13.exchange.user.domain.model.User;
import open.vincentf13.exchange.user.domain.model.UserStatus;
import open.vincentf13.exchange.user.domain.repository.AuthCredentialRepository;
import open.vincentf13.exchange.user.domain.repository.UserRepository;
import open.vincentf13.exchange.user.dto.RegisterUserRequest;
import open.vincentf13.exchange.user.dto.UpdateUserStatusRequest;
import open.vincentf13.exchange.user.dto.UserResponse;
import open.vincentf13.exchange.user.exception.UserAlreadyExistsException;
import open.vincentf13.exchange.user.exception.UserNotFoundException;
import open.vincentf13.exchange.user.mapper.UserDtoMapper;
import open.vincentf13.exchange.user.service.command.RegisterUserCommand;
import open.vincentf13.exchange.user.service.command.UpdateUserStatusCommand;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserApplicationService {

    private final UserRepository userRepository;
    private final AuthCredentialRepository authCredentialRepository;
    private final PasswordEncoder passwordEncoder;

    public UserApplicationService(UserRepository userRepository,
                                  AuthCredentialRepository authCredentialRepository,
                                  PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.authCredentialRepository = authCredentialRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse register(RegisterUserRequest request) {
        String normalizedEmail = request.email().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new UserAlreadyExistsException(normalizedEmail);
        }

        User user = new User();
        user.setEmail(normalizedEmail);
        user.setExternalId(Optional.ofNullable(request.externalId()).orElse(UUID.randomUUID().toString()));
        user.setStatus(UserStatus.ACTIVE);
        userRepository.insert(user);

        AuthCredential credential = buildCredential(user.getId(), request.password());
        authCredentialRepository.insert(credential);

        return UserDtoMapper.toResponse(userRepository.findById(user.getId()).orElse(user));
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
        return UserDtoMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse findByEmail(String email) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new UserNotFoundException(email));
        return UserDtoMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updateStatus(Long id, UpdateUserStatusRequest request) {
        UpdateUserStatusCommand command = new UpdateUserStatusCommand(id, request.status());
        userRepository.findById(command.userId()).orElseThrow(() -> new UserNotFoundException(command.userId()));
        userRepository.updateStatus(command.userId(), command.status());
        User updated = userRepository.findById(command.userId())
                .orElseThrow(() -> new UserNotFoundException(command.userId()));
        return UserDtoMapper.toResponse(updated);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listAll() {
        return userRepository.findAll().stream()
                .map(UserDtoMapper::toResponse)
                .toList();
    }

    private AuthCredential buildCredential(Long userId, String rawPassword) {
        String salt = UUID.randomUUID().toString();
        String encoded = passwordEncoder.encode(rawPassword + ":" + salt);
        AuthCredential credential = new AuthCredential();
        credential.setUserId(userId);
        credential.setCredentialType(AuthCredentialType.PASSWORD);
        credential.setSalt(salt);
        credential.setSecretHash(encoded);
        credential.setStatus("ACTIVE");
        credential.setCreatedAt(Instant.now());
        return credential;
    }
}
