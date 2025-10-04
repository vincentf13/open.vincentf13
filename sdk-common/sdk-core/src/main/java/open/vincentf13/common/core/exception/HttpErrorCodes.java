package open.vincentf13.common.core.exception;

/**
 * HTTP 狀態碼列舉
 * 英文訊息保留，中文含義見每列後方註解
 */
public enum HttpErrorCodes implements ErrorCode {

    // 1xx 資訊回應
    CONTINUE("100", "Continue"),                           // 繼續，客戶端可繼續送出請求主體
    SWITCHING_PROTOCOLS("101", "Switching Protocols"),     // 伺服器同意切換協定
    PROCESSING("102", "Processing"),                       // 伺服器已接收，正在處理（WebDAV）
    EARLY_HINTS("103", "Early Hints"),                     // 提前提示，用於鏈接預載

    // 2xx 成功
    OK("200", "OK"),                                       // 成功
    CREATED("201", "Created"),                             // 已建立資源
    ACCEPTED("202", "Accepted"),                           // 已接受，尚未處理完成
    NON_AUTHORITATIVE_INFORMATION("203", "Non-Authoritative Information"), // 非權威資訊
    NO_CONTENT("204", "No Content"),                       // 成功但無內容
    RESET_CONTENT("205", "Reset Content"),                 // 要求重置文件視圖
    PARTIAL_CONTENT("206", "Partial Content"),             // 部分內容（範圍請求）
    MULTI_STATUS("207", "Multi-Status"),                   // 多狀態（WebDAV）
    ALREADY_REPORTED("208", "Already Reported"),           // 已回報（WebDAV）
    IM_USED("226", "IM Used"),                             // 伺服器完成 GET，回應實例操作結果

    // 3xx 重新導向
    MULTIPLE_CHOICES("300", "Multiple Choices"),           // 多種選擇
    MOVED_PERMANENTLY("301", "Moved Permanently"),         // 永久移動
    FOUND("302", "Found"),                                 // 暫時移動
    SEE_OTHER("303", "See Other"),                         // 參見其他位置
    NOT_MODIFIED("304", "Not Modified"),                   // 未修改（快取）
    USE_PROXY("305", "Use Proxy"),                         // 使用代理（已廢棄）
    TEMPORARY_REDIRECT("307", "Temporary Redirect"),       // 暫時重新導向（方法不變）
    PERMANENT_REDIRECT("308", "Permanent Redirect"),       // 永久重新導向（方法不變）

    // 4xx 用戶端錯誤
    BAD_REQUEST("400", "Bad Request"),                     // 錯誤請求
    UNAUTHORIZED("401", "Unauthorized"),                   // 未認證或 Token 失效
    PAYMENT_REQUIRED("402", "Payment Required"),           // 需付費（保留）
    FORBIDDEN("403", "Forbidden"),                         // 已認證但無權限
    NOT_FOUND("404", "Not Found"),                         // 找不到資源
    METHOD_NOT_ALLOWED("405", "Method Not Allowed"),       // 方法不被允許
    NOT_ACCEPTABLE("406", "Not Acceptable"),               // 無可接受的表示
    PROXY_AUTHENTICATION_REQUIRED("407", "Proxy Authentication Required"), // 需要代理驗證
    REQUEST_TIMEOUT("408", "Request Timeout"),             // 請求逾時
    CONFLICT("409", "Conflict"),                           // 資源衝突
    GONE("410", "Gone"),                                   // 資源已永久消失
    LENGTH_REQUIRED("411", "Length Required"),             // 需要 Content-Length
    PRECONDITION_FAILED("412", "Precondition Failed"),     // 前置條件失敗
    PAYLOAD_TOO_LARGE("413", "Payload Too Large"),         // 請求負載過大
    URI_TOO_LONG("414", "URI Too Long"),                   // URI 過長
    UNSUPPORTED_MEDIA_TYPE("415", "Unsupported Media Type"), // 不支援的媒體型別
    RANGE_NOT_SATISFIABLE("416", "Range Not Satisfiable"), // 範圍請求無法滿足
    EXPECTATION_FAILED("417", "Expectation Failed"),       // Expect 條件失敗
    I_AM_A_TEAPOT("418", "I'm a teapot"),                  // 我是茶壺（玩笑狀態碼）
    MISDIRECTED_REQUEST("421", "Misdirected Request"),     // 請求導向了無法產生回應的伺服器
    UNPROCESSABLE_ENTITY("422", "Unprocessable Entity"),   // 語義正確但無法處理（例如驗證失敗）
    LOCKED("423", "Locked"),                               // 資源被鎖定（WebDAV）
    FAILED_DEPENDENCY("424", "Failed Dependency"),         // 依賴失敗（WebDAV）
    TOO_EARLY("425", "Too Early"),                         // 過早，建議重試（避免重放攻擊）
    UPGRADE_REQUIRED("426", "Upgrade Required"),           // 需要升級協定
    PRECONDITION_REQUIRED("428", "Precondition Required"), // 需要前置條件
    TOO_MANY_REQUESTS("429", "Too Many Requests"),         // 請求過多（限流）
    REQUEST_HEADER_FIELDS_TOO_LARGE("431", "Request Header Fields Too Large"), // 請求標頭過大
    UNAVAILABLE_FOR_LEGAL_REASONS("451", "Unavailable For Legal Reasons"),    // 法律原因不可用

    // 5xx 伺服器錯誤
    INTERNAL_SERVER_ERROR("500", "Internal Server Error"), // 伺服器內部錯誤
    NOT_IMPLEMENTED("501", "Not Implemented"),             // 未實作
    BAD_GATEWAY("502", "Bad Gateway"),                     // 網關錯誤
    SERVICE_UNAVAILABLE("503", "Service Unavailable"),     // 服務不可用（過載或維護）
    GATEWAY_TIMEOUT("504", "Gateway Timeout"),             // 網關逾時
    HTTP_VERSION_NOT_SUPPORTED("505", "HTTP Version Not Supported"), // 不支援的 HTTP 版本
    VARIANT_ALSO_NEGOTIATES("506", "Variant Also Negotiates"),       // 內容協商錯誤
    INSUFFICIENT_STORAGE("507", "Insufficient Storage"),   // 儲存空間不足（WebDAV）
    LOOP_DETECTED("508", "Loop Detected"),                 // 偵測到循環（WebDAV）
    NOT_EXTENDED("510", "Not Extended"),                   // 需進一步擴充
    NETWORK_AUTHENTICATION_REQUIRED("511", "Network Authentication Required"); // 需要網路驗證

    private final String code;       // 數字狀態碼字串
    private final String message;    // 英文訊息（對外訊息維持英文）

    HttpErrorCodes(String code, String message) {
        this.code = code;
        this.message = message;
    }
    @Override public String code() { return code; }
    @Override public String message() { return message; }
}
