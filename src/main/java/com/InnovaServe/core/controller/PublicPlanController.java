package com.InnovaServe.core.controller;

import com.InnovaServe.platform.service.PlatformPlanService;
import com.InnovaServe.platform.entity.Plan;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public read-only catalog for prospective tenants browsing subscription offers. */
@RestController
@RequestMapping("/api/v1/public/plans")
public class PublicPlanController {
  private final PlatformPlanService plans;

  public PublicPlanController(PlatformPlanService plans) {
    this.plans = plans;
  }

  @GetMapping
  public List<Map<String, Object>> list() {
    return plans.list(false).stream().map(this::publicView).toList();
  }

  private Map<String, Object> publicView(Plan plan) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", plan.getId());
    result.put("name", plan.getName());
    result.put("version", plan.getVersion());
    result.put("price", plan.getPrice());
    result.put("currency", plan.getCurrency());
    result.put("duration", plan.getDuration());
    result.put("modules", plan.getModules().stream().sorted().toList());
    return result;
  }
}
