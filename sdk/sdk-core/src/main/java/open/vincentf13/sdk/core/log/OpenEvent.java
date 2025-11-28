package open.vincentf13.sdk.core.log;

/*
 * 定義統一的事件介面，包含事件名稱與預設訊息。
 */
public interface OpenEvent {
    
    String event();
    
    String message();
}
