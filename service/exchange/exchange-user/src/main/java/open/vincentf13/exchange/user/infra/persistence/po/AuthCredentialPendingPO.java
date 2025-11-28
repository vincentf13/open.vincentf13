package open.vincentf13.exchange.user.infra.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.auth.sdk.rest.api.enums.AuthCredentialType;
import open.vincentf13.exchange.user.sdk.rest.api.enums.AuthCredentialPendingStatus;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("auth_credentials_pending")
public class AuthCredentialPendingPO {
    
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    private Long userId;
    private AuthCredentialType credentialType;
    private String secretHash;
    private String salt;
    private AuthCredentialPendingStatus status;
    private Integer retryCount;
    private Instant nextRetryAt;
    private String lastError;
    private Instant createdAt;
    private Instant updatedAt;
}
