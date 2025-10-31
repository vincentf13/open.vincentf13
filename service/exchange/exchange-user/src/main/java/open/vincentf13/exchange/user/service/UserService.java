package open.vincentf13.exchange.user.service;

import lombok.RequiredArgsConstructor;
import open.vincentf13.common.core.OpenMapstruct;
import open.vincentf13.common.core.exception.OpenServiceException;
import open.vincentf13.exchange.user.domain.model.User;
import open.vincentf13.exchange.user.domain.model.UserErrorCode;
import open.vincentf13.exchange.user.infra.persistence.repository.UserRepository;
import open.vincentf13.exchange.user.api.dto.UserRegisterRequest;
import open.vincentf13.exchange.user.api.dto.UserResponse;
import open.vincentf13.exchange.user.api.dto.UserUpdateStatusRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import open.vincentf13.exchange.user.domain.service.UserDomainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserDomainService userDomainService;

    @Transactional
    public UserResponse register(UserRegisterRequest request)  {
        String normalizedEmail = userDomainService.normalizeEmail(request.email());
        boolean emailExists = userRepository.findOne(User.builder().email(normalizedEmail).build()).isPresent();
        if (emailExists) {
            throw OpenServiceException.of(UserErrorCode.USER_ALREADY_EXISTS,
                    "Email already registered: " + normalizedEmail);
        }

        User user = userDomainService.createActiveUser(request.email(), request.externalId());
        userRepository.insertSelective(user);

        return userRepository.findOne(User.builder().id(user.getId()).build())
                .map(user2 -> OpenMapstruct.map(user2, UserResponse.class))
                .orElseThrow(() -> OpenServiceException.of(UserErrorCode.USER_NOT_FOUND,
                        "User not found after creation. id=" + user.getId()));
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        Long userId = currentUserId();
        return userRepository.findOne(User.builder().id(userId).build())
                .map(user -> OpenMapstruct.map(user, UserResponse.class))
                .orElseThrow(() -> OpenServiceException.of(UserErrorCode.USER_NOT_FOUND,
                        "User not found. id=" + userId));
    }

    @Transactional
    public UserResponse updateCurrentUser(UserUpdateStatusRequest request) {
        Long userId = currentUserId();
        userRepository.updateSelective(User.builder()
                .id(userId)
                .status(request.status())
                .updatedAt(Instant.now())
                .build());

        return getCurrentUser();
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw OpenServiceException.of(UserErrorCode.USER_NOT_FOUND, "No authenticated user in context");
        }
        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException ex) {
            throw OpenServiceException.of(UserErrorCode.USER_NOT_FOUND,
                    "Authenticated principal is not a numeric user id: " + authentication.getName());
        }
    }
}
