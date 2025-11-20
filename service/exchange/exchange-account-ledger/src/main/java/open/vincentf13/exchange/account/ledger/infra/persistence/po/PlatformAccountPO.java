package open.vincentf13.exchange.account.ledger.infra.persistence.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformAccountPO {

    private Long accountId;
    private String accountCode;
    private String category;
    private String name;
    private String status;
    private Instant createdAt;
}
