package open.vincentf13.exchange.account.sdk.rest.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountDepositRequest {

 
    private Long userId;

    @NotNull
    private AssetSymbol asset;

    @NotNull
    @DecimalMin(value = ValidationConstant.Names.AMOUNT_MIN)
    private BigDecimal amount;

    @NotNull
    private String txId;

    @NotNull
    private Instant creditedAt;
}
