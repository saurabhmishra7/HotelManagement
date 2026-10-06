package com.InnovaServe.core.controller;

import com.InnovaServe.core.entity.Customer;
import com.InnovaServe.core.service.CustomerService;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {
  private final CustomerService service;

  public CustomerController(CustomerService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_READ')")
  public Page<Customer> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
    return service.list(page, pageSize);
  }

  @GetMapping(params = "phone")
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_READ')")
  public Customer find(@RequestParam String phone) {
    return service.findByPhone(phone);
  }

  @PostMapping
  @PreAuthorize("hasAuthority('PERM_CUSTOMER_WRITE')")
  public Map<String, Object> create(@RequestBody CustomerRequest request) {
    var result =
        service.createOrUpdate(
            request.name(),
            request.phone(),
            request.idProofType(),
            request.idProofNumber(),
            request.idProofTypeOther(),
            request.address());
    return Map.of("id", result.customer().getId(), "is_new_customer", result.isNew());
  }

  public record CustomerRequest(
      String name,
      String phone,
      @JsonProperty("id_proof_type") String idProofType,
      @JsonProperty("id_proof_number") String idProofNumber,
      @JsonProperty("id_proof_type_other") String idProofTypeOther,
      String address) {}
}
