package open.vincentf13.exchange.auth.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.auth.domain.model.AuthCredential;
import open.vincentf13.exchange.auth.infra.persistence.mapper.AuthCredentialMapper;
import open.vincentf13.exchange.auth.infra.persistence.po.AuthCredentialPO;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import open.vincentf13.sdk.core.validator.Id;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Repository
@RequiredArgsConstructor
@Validated
public class AuthCredentialRepository {

  private final AuthCredentialMapper mapper;
  private final DefaultIdGenerator idGenerator;

  public Long insertSelective(@NotNull @Valid AuthCredential credential) {
    if (credential.getId() == null) {
      credential.setId(idGenerator.newLong());
    }
    AuthCredentialPO po = OpenObjectMapper.convert(credential, AuthCredentialPO.class);
    mapper.insert(po);
    return po.getId();
  }

  @Validated({Default.class, Id.class})
  public boolean updateSelective(
      @NotNull @Valid AuthCredential update,
      @NotNull LambdaUpdateWrapper<AuthCredentialPO> updateWrapper) {
    AuthCredentialPO record = OpenObjectMapper.convert(update, AuthCredentialPO.class);
    return mapper.update(record, updateWrapper) > 0;
  }

  public List<AuthCredential> findBy(@NotNull LambdaQueryWrapper<AuthCredentialPO> wrapper) {
    return OpenObjectMapper.convertList(mapper.selectList(wrapper), AuthCredential.class);
  }

  public Optional<AuthCredential> findOne(@NotNull LambdaQueryWrapper<AuthCredentialPO> wrapper) {
    AuthCredentialPO po = mapper.selectOne(wrapper);
    return Optional.ofNullable(OpenObjectMapper.convert(po, AuthCredential.class));
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void batchInsert(@NotEmpty List<AuthCredential> credentials) {
    credentials.forEach(
        credential -> {
          if (credential.getId() == null) {
            credential.setId(idGenerator.newLong());
          }
        });
    List<AuthCredentialPO> records =
        credentials.stream()
            .map(credential -> OpenObjectMapper.convert(credential, AuthCredentialPO.class))
            .toList();

    Db.saveBatch(records);
  }
}
