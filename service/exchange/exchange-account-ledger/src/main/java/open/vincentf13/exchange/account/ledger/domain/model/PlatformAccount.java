package open.vincentf13.exchange.account.ledger.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.PlatformAccountCategory;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.PlatformAccountCode;
import open.vincentf13.exchange.account.ledger.sdk.rest.api.enums.PlatformAccountStatus;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformAccount {

    private Long accountId;
    private PlatformAccountCode accountCode;
    private PlatformAccountCategory category;
    private PlatformAccountStatus status;
    private Instant createdAt;

    public static PlatformAccount createUserDepositAccount() {
        return PlatformAccount.builder()
                .accountCode(PlatformAccountCode.USER_DEPOSIT)
                .category(PlatformAccountCategory.LIABILITY)
                .status(PlatformAccountStatus.ACTIVE)
                .build();
    }
}
