package test.open.vincentf13.sdk.core.test.Sample;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DemoController {
  @GetMapping("/ping")
  Map<String, String> ping(@RequestParam(defaultValue = "World") String name) {
    // 使用最小的 REST 實作提供測試用 API
    return Map.of("message", "Hello " + name);
  }
}
