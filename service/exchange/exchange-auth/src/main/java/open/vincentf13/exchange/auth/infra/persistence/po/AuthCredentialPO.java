package open.vincentf13.exchange.auth.infra.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.auth.sdk.rest.api.enums.AuthCredentialType;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("auth_credentials")
public class AuthCredentialPO {
    
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    
    private Long userId;
    private AuthCredentialType credentialType;
    private String secretHash;
    private String salt;
    private String status;
    private Instant expiresAt;
    private Instant createdAt;
}
