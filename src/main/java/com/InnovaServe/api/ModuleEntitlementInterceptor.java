package com.InnovaServe.api;

import com.InnovaServe.core.security.RequiresModule;
import com.InnovaServe.core.service.ModuleEntitlementService;
import com.InnovaServe.core.service.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ModuleEntitlementInterceptor implements HandlerInterceptor {
  private final ModuleEntitlementService entitlements;
  private final TenantContext tenant;
  private final ObjectMapper objectMapper;

  public ModuleEntitlementInterceptor(
      ModuleEntitlementService entitlements, TenantContext tenant, ObjectMapper objectMapper) {
    this.entitlements = entitlements;
    this.tenant = tenant;
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    if (!(handler instanceof HandlerMethod handlerMethod)) return true;
    RequiresModule requirement =
        AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), RequiresModule.class);
    if (requirement == null)
      requirement =
          AnnotatedElementUtils.findMergedAnnotation(
              handlerMethod.getBeanType(), RequiresModule.class);
    if (requirement == null || entitlements.isActive(tenant.tenantId(), requirement.value()))
      return true;

    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(
        response.getWriter(),
        Map.of(
            "error",
            "MODULE_NOT_ENTITLED",
            "message",
            "This tenant does not have access to the requested module",
            "details",
            Map.of("module", requirement.value().key())));
    return false;
  }
}
