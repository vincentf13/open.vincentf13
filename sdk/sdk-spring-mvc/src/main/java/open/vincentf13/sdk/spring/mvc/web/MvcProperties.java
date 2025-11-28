package open.vincentf13.sdk.spring.mvc.web;

import lombok.Getter;
import lombok.Setter;
import open.vincentf13.sdk.core.OpenConstant;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/*
  只保留框架未提供的 MVC 自訂設定（請求追蹤與回應包裝）。
 */
@Validated
@Getter
@ConfigurationProperties(prefix = OpenConstant.Package.Names.BASE_PACKAGE + ".mvc")
public class MvcProperties {

    @NestedConfigurationProperty
    private final Request request = new Request();

    @NestedConfigurationProperty
    private final Response response = new Response();

    /*
  MVC 請求相關設定（追蹤資訊、Filter 順序）。
 */
    @Getter
    @Setter
    public static class Request {
        /**
         * 是否在請求缺少 header 時自動生成追蹤資訊。
         */
        private boolean generateCorrelationIds = true;
        private boolean writeResponseHeader = true;
        /*
  Filter 註冊順序，預設值讓追蹤資訊最先寫入。
 */
        private int filterOrder = Integer.MIN_VALUE; // highest precedence by default
    }

    /*
  REST 回應包裝相關設定。
 */
    @Getter
    @Setter
    public static class Response {
        /*
  是否對 REST 回應做統一包裝。
 */
        private boolean wrapEnabled = true;
        /*
  不進行包裝的 controller name pattern，支援前綴比對。
 */
        private List<String> ignoreControllerPrefixes = new ArrayList<>();
    }

}
