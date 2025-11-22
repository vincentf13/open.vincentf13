package open.vincentf13.exchange.account.ledger.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformAccount {

    public static final String CATEGORY_ASSET = "ASSET";
    public static final String CATEGORY_LIABILITY = "LIABILITY";
    public static final String CATEGORY_REVENUE = "REVENUE";
    public static final String CATEGORY_EXPENSE = "EXPENSE";
    public static final String CATEGORY_EQUITY = "EQUITY";

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";

    public static final String ACCOUNT_CODE_USER_DEPOSIT = "USER_DEPOSIT";
    public static final String ACCOUNT_NAME_USER_DEPOSIT = "User Deposit";

    private Long accountId;
    private String accountCode;
    private String category;
    private String name;
    private String status;
    private Instant createdAt;

    public static PlatformAccount createUserDepositAccount() {
        Instant now = Instant.now();
        return PlatformAccount.builder()
                .accountCode(ACCOUNT_CODE_USER_DEPOSIT)
                .category(CATEGORY_LIABILITY)
                .name(ACCOUNT_NAME_USER_DEPOSIT)
                .status(STATUS_ACTIVE)
                .createdAt(now)
                .build();
    }
}
