package open.vincentf13.sdk.auth.apikey.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an API endpoint as private, meaning it requires API Key authentication.
 * This is typically used for server-to-server communication.
 * This annotation can be applied to a method or an entire class.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PrivateAPI {
}
