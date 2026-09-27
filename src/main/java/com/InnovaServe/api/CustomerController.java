package com.InnovaServe.api;

import com.InnovaServe.core.entity.Customer;
import com.InnovaServe.core.service.CustomerService;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {
  private final CustomerService service;

  public CustomerController(CustomerService service) {
    this.service = service;
  }

  @GetMapping
  public Page<Customer> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(name = "page_size", defaultValue = "20") int pageSize) {
    return service.list(page, pageSize);
  }

  @GetMapping(params = "phone")
  public Customer find(@RequestParam String phone) {
    return service.findByPhone(phone);
  }

  @PostMapping
  public Map<String, Object> create(@RequestBody CustomerRequest request) {
    var result =
        service.createOrUpdate(
            request.name(),
            request.phone(),
            request.idProofType(),
            request.idProofNumber(),
            request.address());
    return Map.of("id", result.customer().getId(), "is_new_customer", result.isNew());
  }

  public record CustomerRequest(
      String name,
      String phone,
      @JsonProperty("id_proof_type") String idProofType,
      @JsonProperty("id_proof_number") String idProofNumber,
      String address) {}
}
