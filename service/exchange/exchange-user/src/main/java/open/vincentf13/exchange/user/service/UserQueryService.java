package open.vincentf13.exchange.user.service;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.user.domain.model.User;
import open.vincentf13.exchange.user.infra.UserErrorCodeEnum;
import open.vincentf13.exchange.user.infra.persistence.repository.UserRepository;
import open.vincentf13.exchange.user.sdk.rest.api.dto.UserResponse;
import open.vincentf13.sdk.auth.jwt.OpenJwtLoginUserInfo;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import open.vincentf13.sdk.core.exception.OpenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserQueryService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        Long userId = OpenJwtLoginUserInfo.currentUserIdOrThrow(() ->
                OpenException.of(UserErrorCodeEnum.USER_NOT_FOUND, Map.of("reason", "No authenticated user in context")));
        return userRepository.findOne(User.builder().id(userId).build())
                .map(user -> OpenObjectMapper.convert(user, UserResponse.class))
                .orElseThrow(() -> OpenException.of(UserErrorCodeEnum.USER_NOT_FOUND,
                                                    Map.of("userId", userId)));
    }

    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email) {
        String normalizedEmail = User.normalizeEmail(email);
        return userRepository.findOne(User.builder().email(normalizedEmail).build())
                .map(user -> OpenObjectMapper.convert(user, UserResponse.class))
                .orElseThrow(() -> OpenException.of(UserErrorCodeEnum.USER_NOT_FOUND,
                                                    Map.of("email", normalizedEmail)));
    }
}
