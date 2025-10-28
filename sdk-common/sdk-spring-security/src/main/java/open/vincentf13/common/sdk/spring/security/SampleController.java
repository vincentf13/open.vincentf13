//package open.vincentf13.common.sdk.spring.security;
//
//import org.springframework.web.bind.annotation.GetMapping;
//
//public class SampleController {
//    @GetMapping("/me")
//    public Map<String, Object> me(Authentication auth) {
//        return Map.of(
//                "username", auth.getName(),
//                "authorities", auth.getAuthorities(),
//                "details", auth.getDetails()
//                     );
//    }
//}
