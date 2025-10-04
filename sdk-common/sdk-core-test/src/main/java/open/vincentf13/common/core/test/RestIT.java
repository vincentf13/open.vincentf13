package open.vincentf13.common.core.test;

import open.vincentf13.common.core.test.contract.AbstractIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// REST 整合測試：用 MockMvc 驗證 Controller 在完整 Spring Context 下的行為
@AutoConfigureMockMvc
@Import(RestIT.RestApi.class)
class RestIT extends AbstractIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void httpEndpointRespondsWithJson() throws Exception {
        // 呼叫內嵌 Controller 並檢查 HTTP 狀態、Content-Type 與回傳 JSON
        MvcResult result = mockMvc.perform(get("/api/ping").param("name", "Codex"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Hello Codex"))
                .andReturn();

    }

    @TestConfiguration
    static class RestApi {

        @RestController
        @RequestMapping("/api")
        static class PingController {
            @GetMapping("/ping")
            Map<String, String> ping(@RequestParam(defaultValue = "World") String name) {
                // 使用最小的 REST 實作提供測試用 API
                return Map.of("message", "Hello " + name);
            }
        }
    }
}
