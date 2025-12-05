package open.vincentf13.exchange.account.domain.service.result;

import open.vincentf13.exchange.account.domain.model.PlatformAccount;
import open.vincentf13.exchange.account.domain.model.PlatformJournal;
import open.vincentf13.exchange.account.domain.model.UserAccount;
import open.vincentf13.exchange.account.domain.model.UserJournal;

public record AccountWithdrawalResult(
        UserAccount userAssetAccount,
        UserAccount userEquityAccount,
        PlatformAccount platformAssetAccount,
        PlatformAccount platformLiabilityAccount,
        UserJournal userAssetJournal,
        UserJournal userEquityJournal,
        PlatformJournal platformAssetJournal,
        PlatformJournal platformLiabilityJournal
) {
}
