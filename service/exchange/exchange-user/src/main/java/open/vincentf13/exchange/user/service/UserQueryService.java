package open.vincentf13.exchange.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.user.domain.model.User;
import open.vincentf13.exchange.user.infra.UserErrorCode;
import open.vincentf13.exchange.user.infra.persistence.po.UserPO;
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
                OpenException.of(UserErrorCode.USER_NOT_FOUND, Map.of("reason", "No authenticated user in context")));
        return userRepository.findOne(Wrappers.<UserPO>lambdaQuery()
                        .eq(UserPO::getId, userId))
                .map(user -> OpenObjectMapper.convert(user, UserResponse.class))
                .orElseThrow(() -> OpenException.of(UserErrorCode.USER_NOT_FOUND,
                                                    Map.of("userId", userId)));
    }

    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email) {
        String normalizedEmail = User.normalizeEmail(email);
        return userRepository.findOne(Wrappers.<UserPO>lambdaQuery()
                        .eq(UserPO::getEmail, normalizedEmail))
                .map(user -> OpenObjectMapper.convert(user, UserResponse.class))
                .orElseThrow(() -> OpenException.of(UserErrorCode.USER_NOT_FOUND,
                                                    Map.of("email", normalizedEmail)));
    }
}
