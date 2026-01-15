package open.vincentf13.exchange.account.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.domain.model.UserAccount;
import open.vincentf13.exchange.account.infra.persistence.mapper.UserAccountMapper;
import open.vincentf13.exchange.account.infra.persistence.po.UserAccountPO;
import open.vincentf13.exchange.account.sdk.rest.api.enums.AccountCategory;
import open.vincentf13.exchange.account.sdk.rest.api.enums.UserAccountCode;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.sdk.core.mapper.OpenObjectMapper;
import open.vincentf13.sdk.core.validator.Id;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.validation.annotation.Validated;

@Repository
@Validated
@RequiredArgsConstructor
public class UserAccountRepository {

  private static final Long SPOT_INSTRUMENT_ID = 0L;
  private final UserAccountMapper mapper;
  private final DefaultIdGenerator idGenerator;

  public UserAccount insertSelective(@NotNull @Valid UserAccount balance) {
    if (balance.getAccountId() == null) {
      balance.setAccountId(idGenerator.newLong());
    }
    if (balance.getInstrumentId() == null) {
      balance.setInstrumentId(SPOT_INSTRUMENT_ID);
    }
    if (balance.getAccountName() == null && balance.getAccountCode() != null) {
      balance.setAccountName(balance.getAccountCode().getDisplayName());
    }
    if (balance.getBalance() == null) {
      balance.setBalance(BigDecimal.ZERO);
    }
    if (balance.getAvailable() == null) {
      balance.setAvailable(BigDecimal.ZERO);
    }
    if (balance.getReserved() == null) {
      balance.setReserved(BigDecimal.ZERO);
    }
    if (balance.getVersion() == null) {
      balance.setVersion(0);
    }
    UserAccountPO po = OpenObjectMapper.convert(balance, UserAccountPO.class);
    mapper.insert(po);
    return balance;
  }

  @Validated({Default.class, Id.class})
  public boolean updateSelectiveBy(
      @NotNull @Valid UserAccount balance, LambdaUpdateWrapper<UserAccountPO> updateWrapper) {
    if (balance.getInstrumentId() == null) {
      balance.setInstrumentId(SPOT_INSTRUMENT_ID);
    }
    UserAccountPO po = OpenObjectMapper.convert(balance, UserAccountPO.class);
    return mapper.update(po, updateWrapper) > 0;
  }

  @Validated({Default.class, Id.class})
  public void updateSelectiveBatch(
      @NotNull List<@Valid UserAccount> accounts,
      @NotNull List<Integer> expectedVersions,
      @NotNull String action) {
    if (accounts.size() != expectedVersions.size()) {
      throw new IllegalArgumentException("accounts size does not match expectedVersions size");
    }

    record UpdateTask(UserAccountPO po, Integer expectedVersion) {}
    List<UpdateTask> tasks = new java.util.ArrayList<>(accounts.size());
    for (int i = 0; i < accounts.size(); i++) {
      UserAccount account = accounts.get(i);
      if (account.getInstrumentId() == null) {
        account.setInstrumentId(SPOT_INSTRUMENT_ID);
      }
      tasks.add(
          new UpdateTask(
              OpenObjectMapper.convert(account, UserAccountPO.class), expectedVersions.get(i)));
    }

    int updatedRows = mapper.batchUpdateWithOptimisticLock(tasks);
    if (updatedRows != accounts.size()) {
      throw new OptimisticLockingFailureException(
          "Batch update failed due to optimistic locking. Expected: "
              + accounts.size()
              + ", Actual: "
              + updatedRows);
    }
  }

  public List<UserAccount> findBy(@NotNull LambdaQueryWrapper<UserAccountPO> wrapper) {
    return mapper.selectList(wrapper).stream()
        .map(item -> normalizeInstrumentId(OpenObjectMapper.convert(item, UserAccount.class)))
        .toList();
  }

  public Optional<UserAccount> findOne(@NotNull LambdaQueryWrapper<UserAccountPO> wrapper) {
    UserAccountPO po = mapper.selectOne(wrapper);
    return Optional.ofNullable(
        normalizeInstrumentId(OpenObjectMapper.convert(po, UserAccount.class)));
  }

  public List<UserAccount> findByUserId(@NotNull Long userId) {
    return findBy(Wrappers.lambdaQuery(UserAccountPO.class).eq(UserAccountPO::getUserId, userId));
  }

  public List<UserAccount> findByUserIds(@NotNull List<Long> userIds) {
    Set<Long> unique = userIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    if (unique.isEmpty()) {
      return List.of();
    }
    return findBy(Wrappers.lambdaQuery(UserAccountPO.class).in(UserAccountPO::getUserId, unique));
  }

  public UserAccount getOrCreate(
      @NotNull Long userId,
      @NotNull UserAccountCode accountCode,
      Long instrumentId,
      @NotNull AssetSymbol asset) {
    Long normalizedInstrumentId = normalizeInstrumentIdValue(instrumentId);
    String accountName = accountCode.getDisplayName();
    AccountCategory category = accountCode.getCategory();
    return findOne(
            Wrappers.lambdaQuery(UserAccountPO.class)
                .eq(UserAccountPO::getUserId, userId)
                .eq(UserAccountPO::getAccountCode, accountCode)
                .eq(UserAccountPO::getCategory, category)
                .eq(UserAccountPO::getAsset, asset)
                .eq(UserAccountPO::getInstrumentId, normalizedInstrumentId))
        .map(
            account -> {
              if (account.getAccountName() == null) {
                account.setAccountName(accountName);
              }
              return account;
            })
        .orElseGet(
            () ->
                insertSelective(
                    UserAccount.createDefault(userId, accountCode, normalizedInstrumentId, asset)));
  }

  private Long normalizeInstrumentIdValue(Long instrumentId) {
    return instrumentId == null ? SPOT_INSTRUMENT_ID : instrumentId;
  }

  private UserAccount normalizeInstrumentId(UserAccount account) {
    if (account == null) {
      return null;
    }
    if (account.getInstrumentId() == null) {
      account.setInstrumentId(SPOT_INSTRUMENT_ID);
    }
    return account;
  }
}
