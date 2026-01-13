package open.vincentf13.sdk.core.validator;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.groups.Default;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class OpenValidator {

  private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
  private static final Validator VALIDATOR = FACTORY.getValidator();

  private OpenValidator() {}

  public static <T> Set<String> validate(T bean) {
    return validate(bean, Default.class);
  }

  /**
   * 規則： - 未標註 groups 的約束屬於 Default Group。 - ID 欄位使用 Id group（用於更新/刪除）。
   *
   * <p>DTO 範例： public class UserDTO { @NotNull(groups = Id.class) private Long id; @NotBlank <-
   * Default Group private String name; }
   *
   * <p>用法： - Insert：OpenValidator.validate(bean) 或 validateOrThrow(bean) <- 會效驗Default Group 的規則 -
   * Update/Delete：OpenValidator.validate(bean, Default.class, Id.class) <- 會效驗Default Group + Id的規則
   *
   * <p>Spring @Validated 規則： - @Valid 只觸發驗證，不帶 group（Default）。 - Update/Delete
   * 要在方法上加，如下註解以傳遞Group： @Validated({Default.class, Id.class}) public void update(@Valid Model
   * model) { ... } - 不傳 group 時預設使用 Default。
   */
  public static <T> Set<String> validate(T bean, Class<?>... groups) {
    Objects.requireNonNull(bean, "Validation target must not be null");
    Class<?>[] activeGroups = normalizeGroups(groups);
    return VALIDATOR.validate(bean, activeGroups).stream()
        .map(v -> v.getPropertyPath() + " " + v.getMessage())
        .collect(Collectors.toSet());
  }

  public static <T> void validateOrThrow(T bean) {
    validateOrThrow(bean, Default.class);
  }

  /**
   * 規則： - 未標註 groups 的約束屬於 Default Group。 - ID 欄位使用 Id group（用於更新/刪除）。
   *
   * <p>DTO 範例： public class UserDTO { @NotNull(groups = Id.class) private Long id; @NotBlank <-
   * Default Group private String name; }
   *
   * <p>用法： - Insert：OpenValidator.validateOrThrow(bean) <- 會效驗Default Group 的規則 -
   * Update/Delete：OpenValidator.validateOrThrow(bean, Default.class, Id.class) <- 會效驗Default Group
   * + Id的規則
   *
   * <p>Spring @Validated 規則： - @Valid 只觸發驗證，不帶 group（Default）。 - Update/Delete
   * 要在方法上加，如下註解以傳遞Group： @Validated({Default.class, Id.class}) public void update(@Valid Model
   * model) { ... } - 不傳 group 時預設使用 Default。
   */
  public static <T> void validateOrThrow(T bean, Class<?>... groups) {
    Objects.requireNonNull(bean, "Validation target must not be null");
    Class<?>[] activeGroups = normalizeGroups(groups);
    Set<ConstraintViolation<T>> violations = VALIDATOR.validate(bean, activeGroups);
    if (!violations.isEmpty()) {
      String msg =
          violations.stream()
              .map(v -> v.getPropertyPath() + " " + v.getMessage())
              .collect(Collectors.joining("; "));
      throw new IllegalArgumentException("Validation failed: " + msg);
    }
  }

  private static Class<?>[] normalizeGroups(Class<?>... groups) {
    if (groups == null || groups.length == 0) {
      return new Class<?>[] {Default.class};
    }
    return groups;
  }
}
