package open.vincentf13.common.core.test;

import open.vincentf13.common.core.test.contract.AbstractIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(RestIT.RestApi.class)
class RestIT extends AbstractIT {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void httpEndpointRespondsWithJson() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/ping").param("name", "Codex"))
        .andExpect(status().isOk())
        .andReturn();

    Jsons.assertEquals("{\"message\":\"Hello Codex\"}", result.getResponse().getContentAsString());
  }

  @TestConfiguration
  static class RestApi {

    @RestController
    @RequestMapping("/api")
    static class PingController {
      @GetMapping("/ping")
      Map<String, String> ping(@RequestParam(defaultValue = "World") String name) {
        return Map.of("message", "Hello " + name);
      }
    }
  }
}
