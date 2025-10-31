package open.vincentf13.exchange.user.service;

import lombok.RequiredArgsConstructor;
import open.vincentf13.common.core.exception.OpenServiceException;
import open.vincentf13.exchange.user.domain.error.UserErrorCode;
import open.vincentf13.common.core.OpenMapstruct;
import open.vincentf13.exchange.user.domain.model.AuthCredential;
import open.vincentf13.exchange.user.domain.model.AuthCredentialType;
import open.vincentf13.exchange.user.domain.model.User;
import open.vincentf13.exchange.user.infra.persistence.repository.AuthCredentialRepository;
import open.vincentf13.exchange.user.infra.persistence.repository.UserRepository;
import open.vincentf13.exchange.user.api.dto.RegisterUserRequest;
import open.vincentf13.exchange.user.api.dto.UpdateUserStatusRequest;
import open.vincentf13.exchange.user.api.dto.UserResponse;
import open.vincentf13.exchange.user.domain.service.UserDomainService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AuthCredentialRepository authCredentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserDomainService userDomainService;

    @Transactional
    public UserResponse register(RegisterUserRequest request)  {
        String normalizedEmail = userDomainService.normalizeEmail(request.email());
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw OpenServiceException.of(UserErrorCode.USER_ALREADY_EXISTS,
                    "Email already registered: " + normalizedEmail);
        }

        User user = userDomainService.createActiveUser(request.email(), request.externalId());
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
                .map(user2 -> OpenMapstruct.map(user2, UserResponse.class))
                .orElseThrow(() -> OpenServiceException.of(UserErrorCode.USER_NOT_FOUND,
                        "User not found after creation. id=" + user.getId()));
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id)  {
        return userRepository.findById(id)
                .map(user -> OpenMapstruct.map(user, UserResponse.class))
                .orElseThrow(() -> OpenServiceException.of(UserErrorCode.USER_NOT_FOUND,
                        "User not found. id=" + id));
    }

    @Transactional(readOnly = true)
    public UserResponse findByEmail(String email)  {
        return userRepository.findByEmail(userDomainService.normalizeEmail(email))
                .map(user -> OpenMapstruct.map(user, UserResponse.class))
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
                .map(user -> OpenMapstruct.map(user, UserResponse.class))
                .orElseThrow(() -> OpenServiceException.of(UserErrorCode.USER_NOT_FOUND,
                        "User not found. id=" + id));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listAll() {
        return OpenMapstruct.mapList(userRepository.findAll(), UserResponse.class);
    }

    private String generateSalt() {
        return UUID.randomUUID().toString();
    }

    private String hashPassword(String rawPassword, String salt) {
        return passwordEncoder.encode(rawPassword + ':' + salt);
    }
}
