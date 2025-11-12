package open.vincentf13.exchange.user.service;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.user.domain.model.User;
import open.vincentf13.exchange.user.domain.model.UserErrorCode;
import open.vincentf13.exchange.user.domain.service.UserDomainService;
import open.vincentf13.exchange.user.infra.persistence.repository.UserRepository;
import open.vincentf13.exchange.user.sdk.rest.api.dto.UserResponse;
import open.vincentf13.sdk.auth.jwt.OpenJwtLoginUserInfo;
import open.vincentf13.sdk.core.OpenMapstruct;
import open.vincentf13.sdk.core.exception.OpenServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserQueryService {

    private final UserRepository userRepository;
    private final UserDomainService userDomainService;

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        Long userId = OpenJwtLoginUserInfo.currentUserIdOrThrow(() ->
                OpenServiceException.of(UserErrorCode.USER_NOT_FOUND, "No authenticated user in context"));
        return userRepository.findOne(User.builder().id(userId).build())
                .map(user -> OpenMapstruct.map(user, UserResponse.class))
                .orElseThrow(() -> OpenServiceException.of(UserErrorCode.USER_NOT_FOUND,
                        "User not found. id=" + userId));
    }

    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email) {
        String normalizedEmail = userDomainService.normalizeEmail(email);
        return userRepository.findOne(User.builder().email(normalizedEmail).build())
                .map(user -> OpenMapstruct.map(user, UserResponse.class))
                .orElseThrow(() -> OpenServiceException.of(UserErrorCode.USER_NOT_FOUND,
                        "User not found. email=" + normalizedEmail));
    }
}
