package open.vincentf13.common.spring.session;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.session.FlushMode;
import org.springframework.session.SaveMode;

@ConfigurationProperties(prefix = "open.vincentf13.session")
public class OpenSessionProperties {

    private Duration maxInactiveInterval = Duration.ofMinutes(30);

    private FlushMode flushMode = FlushMode.ON_SAVE;

    private SaveMode saveMode = SaveMode.ON_SET_ATTRIBUTE;

    private String namespace = "open:session";

    private final Cookie cookie = new Cookie();

    public Duration getMaxInactiveInterval() {
        return this.maxInactiveInterval;
    }

    public void setMaxInactiveInterval(Duration maxInactiveInterval) {
        this.maxInactiveInterval = maxInactiveInterval;
    }

    public FlushMode getFlushMode() {
        return this.flushMode;
    }

    public void setFlushMode(FlushMode flushMode) {
        this.flushMode = flushMode;
    }

    public SaveMode getSaveMode() {
        return this.saveMode;
    }

    public void setSaveMode(SaveMode saveMode) {
        this.saveMode = saveMode;
    }

    public String getNamespace() {
        return this.namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public Cookie getCookie() {
        return this.cookie;
    }

    public static final class Cookie {

        private String name = "OPEN_SESSION";

        private String domain;

        private String path = "/";

        private Duration maxAge;

        private boolean httpOnly = true;

        private SameSite sameSite = SameSite.LAX;

        private Secure secure = Secure.AUTO;

        private boolean useBase64Encoding = false;

        private String rememberMeRequestAttribute;

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDomain() {
            return this.domain;
        }

        public void setDomain(String domain) {
            this.domain = domain;
        }

        public String getPath() {
            return this.path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public Duration getMaxAge() {
            return this.maxAge;
        }

        public void setMaxAge(Duration maxAge) {
            this.maxAge = maxAge;
        }

        public boolean isHttpOnly() {
            return this.httpOnly;
        }

        public void setHttpOnly(boolean httpOnly) {
            this.httpOnly = httpOnly;
        }

        public SameSite getSameSite() {
            return this.sameSite;
        }

        public void setSameSite(SameSite sameSite) {
            this.sameSite = sameSite;
        }

        public Secure getSecure() {
            return this.secure;
        }

        public void setSecure(Secure secure) {
            this.secure = secure;
        }

        public boolean isUseBase64Encoding() {
            return this.useBase64Encoding;
        }

        public void setUseBase64Encoding(boolean useBase64Encoding) {
            this.useBase64Encoding = useBase64Encoding;
        }

        public String getRememberMeRequestAttribute() {
            return this.rememberMeRequestAttribute;
        }

        public void setRememberMeRequestAttribute(String rememberMeRequestAttribute) {
            this.rememberMeRequestAttribute = rememberMeRequestAttribute;
        }

        public enum SameSite {
            LAX("Lax"),
            STRICT("Strict"),
            NONE("None"),
            UNSPECIFIED(null);

            private final String attributeValue;

            SameSite(String attributeValue) {
                this.attributeValue = attributeValue;
            }

            public String getAttributeValue() {
                return this.attributeValue;
            }
        }

        public enum Secure {
            ALWAYS,
            AUTO,
            NEVER
        }
    }
}
