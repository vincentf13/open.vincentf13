package open.vincentf13.sdk.auth.auth;

import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@AuthRequirement(AuthType.SESSION)
public @interface SessionAuth {}
