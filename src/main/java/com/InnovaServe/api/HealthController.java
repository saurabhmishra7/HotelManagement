package com.InnovaServe.api;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
/** Process readiness: only reachable after Spring has initialized (including Flyway). */
@RestController
public class HealthController {
  @GetMapping("/health") public Map<String, String> health() { return Map.of("status", "UP"); }
}
