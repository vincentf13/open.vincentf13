package test.open.vincentf13.common.core.test;


import test.open.vincentf13.common.core.test.Sample.DemoController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// REST 切片測試：用 MockMvc 在精簡 WebMVC Context 中驗證 Controller 行為
@WebMvcTest(controllers = DemoController.class)
class ApiTest {
    @Autowired
    MockMvc mockMvc;

    @Test
    void httpEndpointRespondsWithJson() throws Exception {
        // 呼叫內嵌 Controller 並檢查 HTTP 狀態、Content-Type 與回傳 JSON
        mockMvc.perform(get("/api/ping").param("name", "Codex"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Hello Codex"));
    }
}
