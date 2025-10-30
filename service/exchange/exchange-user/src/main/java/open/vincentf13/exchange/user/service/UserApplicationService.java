package open.vincentf13.exchange.user.service;

import lombok.RequiredArgsConstructor;
import open.vincentf13.common.core.exception.OpenServiceException;
import open.vincentf13.exchange.user.domain.error.UserErrorCode;
import open.vincentf13.exchange.user.domain.model.AuthCredential;
import open.vincentf13.exchange.user.domain.model.AuthCredentialType;
import open.vincentf13.exchange.user.domain.model.User;
import open.vincentf13.exchange.user.domain.model.UserStatus;
import open.vincentf13.exchange.user.infra.repository.AuthCredentialRepository;
import open.vincentf13.exchange.user.infra.repository.UserRepository;
import open.vincentf13.exchange.user.dto.RegisterUserRequest;
import open.vincentf13.exchange.user.dto.UpdateUserStatusRequest;
import open.vincentf13.exchange.user.dto.UserResponse;
import open.vincentf13.exchange.user.mapper.UserDtoMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserApplicationService {

    private final UserRepository userRepository;
    private final AuthCredentialRepository authCredentialRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse register(RegisterUserRequest request)  {
        String normalizedEmail = request.email().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw OpenServiceException.of(UserErrorCode.USER_ALREADY_EXISTS,
                    "Email already registered: " + normalizedEmail);
        }

        User user = User.builder()
                .email(normalizedEmail)
                .externalId(Optional.ofNullable(request.externalId()).orElse(UUID.randomUUID().toString()))
                .status(UserStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        userRepository.insert(user);

        String salt = generateSalt();
        AuthCredential credential = AuthCredential.builder()
                .userId(user.getId())
                .credentialType(AuthCredentialType.PASSWORD)
                .salt(salt)
                .secretHash(hashPassword(request.password(), salt))
                .status("ACTIVE")
                .createdAt(Instant.now())
                .build();
        authCredentialRepository.insert(credential);

        return userRepository.findById(user.getId())
                .map(UserDtoMapper::toResponse)
                .orElseThrow(() -> OpenServiceException.of(UserErrorCode.USER_NOT_FOUND,
                        "User not found after creation. id=" + user.getId()));
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id)  {
        return userRepository.findById(id)
                .map(UserDtoMapper::toResponse)
                .orElseThrow(() -> OpenServiceException.of(UserErrorCode.USER_NOT_FOUND,
                        "User not found. id=" + id));
    }

    @Transactional(readOnly = true)
    public UserResponse findByEmail(String email)  {
        return userRepository.findByEmail(email.toLowerCase())
                .map(UserDtoMapper::toResponse)
                .orElseThrow(() -> OpenServiceException.of(UserErrorCode.USER_NOT_FOUND,
                        "User not found. email=" + email));
    }

    @Transactional
    public UserResponse updateStatus(Long id, UpdateUserStatusRequest request)  {
        userRepository.findById(id)
                .orElseThrow(() -> OpenServiceException.of(UserErrorCode.USER_NOT_FOUND,
                        "User not found. id=" + id));
        userRepository.updateStatus(id, request.status());
        return userRepository.findById(id)
                .map(UserDtoMapper::toResponse)
                .orElseThrow(() -> OpenServiceException.of(UserErrorCode.USER_NOT_FOUND,
                        "User not found. id=" + id));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listAll() {
        return userRepository.findAll().stream()
                .map(UserDtoMapper::toResponse)
                .toList();
    }

    private String generateSalt() {
        return UUID.randomUUID().toString();
    }

    private String hashPassword(String rawPassword, String salt) {
        return passwordEncoder.encode(rawPassword + ':' + salt);
    }
}
