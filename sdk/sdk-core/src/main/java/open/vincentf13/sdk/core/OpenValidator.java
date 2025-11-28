package open.vincentf13.sdk.core;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/*
  通用驗證工具類，基於 Jakarta Bean Validation (JSR 380)

  <p>功能：
  - 對任意物件執行校驗（依據 @NotNull、@Min、@Size 等註解）
  - 支援回傳錯誤訊息集合，或直接丟出例外

  <p>使用說明：

  <pre>{@code
  // 1. 定義實體類
  public class User {
  @NotBlank
  private String name;

  @Min(1)
  private int age;
  }

  // 2. 驗證並回傳錯誤訊息集合
  Set<String> errors = OpenValidator.validate(user);
  if (!errors.isEmpty()) {
  errors.forEach(System.out::println);
  }

  // 3. 驗證並在有錯時丟出 IllegalArgumentException
  OpenValidator.validateOrThrow(user);
  }</pre>
 */
public final class OpenValidator {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    private OpenValidator() {}

    /*
  驗證物件，回傳錯誤訊息集合。

  @param bean 欲驗證物件
  @param <T>  類型
  @return 錯誤訊息集合，若無違規則回傳空集合
 */
    public static <T> Set<String> validate(T bean) {
        Objects.requireNonNull(bean, "Validation target must not be null");
        return VALIDATOR.validate(bean).stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .collect(Collectors.toSet());
    }

    /*
  驗證物件，若存在違規則丟出 IllegalArgumentException。

  @param bean 欲驗證物件
  @param <T>  類型
  @throws IllegalArgumentException 驗證失敗時
 */
    public static <T> void validateOrThrow(T bean) {
        Objects.requireNonNull(bean, "Validation target must not be null");
        Set<ConstraintViolation<T>> violations = VALIDATOR.validate(bean);
        if (!violations.isEmpty()) {
            String msg = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining("; "));
            throw new IllegalArgumentException("Validation failed: " + msg);
        }
    }
}
