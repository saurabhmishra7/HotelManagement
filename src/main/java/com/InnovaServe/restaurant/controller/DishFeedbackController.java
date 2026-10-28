package com.InnovaServe.restaurant.controller;

import com.InnovaServe.core.security.ModuleType;
import com.InnovaServe.core.security.RequiresModule;
import com.InnovaServe.restaurant.service.DishFeedbackService;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/restaurant/feedback")
@RequiresModule(ModuleType.RESTAURANT)
public class DishFeedbackController {
  private final DishFeedbackService feedback;

  public DishFeedbackController(DishFeedbackService feedback) { this.feedback = feedback; }

  @GetMapping
  @PreAuthorize("hasAnyAuthority('PERM_MENU_READ', 'PERM_TENANT_PROFILE_MANAGE')")
  public List<Map<String, Object>> recent() { return feedback.recent(); }
}
