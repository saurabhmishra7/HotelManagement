package com.InnovaServe.core.service;

import com.InnovaServe.contracts.StayLookupPort;
import com.InnovaServe.core.entity.Customer;
import com.InnovaServe.core.repository.CustomerRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CustomerService {
  private final CustomerRepository repository;
  private final TenantContext tenant;
  private final StayLookupPort stays;

  public CustomerService(
      CustomerRepository repository, TenantContext tenant, StayLookupPort stays) {
    this.repository = repository;
    this.tenant = tenant;
    this.stays = stays;
  }

  public Page<Customer> list(int page, int size, String stayStatus) {
    UUID tenantId = tenant.tenantId();
    Pageable pageable = PageRequest.of(
        Math.max(0, page), Math.clamp(size, 1, 100), Sort.by("name").ascending());
    Page<Customer> customers;

    if ("all".equals(stayStatus)) {
      customers = repository.findAllByTenantId(tenantId, pageable);
    } else {
      Pageable statusPageable = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 100));
      StayLookupPort.CustomerIdPage customerIds = stays.findCustomerIdsByStayHistory(
          tenantId, "stayed".equals(stayStatus), statusPageable.getPageNumber(),
          statusPageable.getPageSize());
      Map<UUID, Customer> customersById = repository
          .findAllByTenantIdAndIdIn(tenantId, customerIds.customerIds())
          .stream()
          .collect(Collectors.toMap(Customer::getId, customer -> customer));
      List<Customer> orderedCustomers = customerIds.customerIds().stream()
          .map(customersById::get)
          .filter(java.util.Objects::nonNull)
          .toList();
      customers = new PageImpl<>(orderedCustomers, statusPageable, customerIds.totalElements());
    }

    attachStayHistory(tenantId, customers.getContent());
    return customers;
  }

  public Customer findByPhone(String phone) {
    UUID tenantId = tenant.tenantId();
    Customer customer = repository.findByTenantIdAndPhone(tenantId, phone).orElse(null);
    if (customer == null) return null;
    attachStayHistory(tenantId, List.of(customer));
    return customer;
  }

  private void attachStayHistory(UUID tenantId, List<Customer> customers) {
    if (customers.isEmpty()) return;
    Map<UUID, List<Customer.StayRecord>> histories = stays.findCustomerStayHistory(
            tenantId, customers.stream().map(Customer::getId).toList())
        .stream()
        .collect(Collectors.groupingBy(
            StayLookupPort.CustomerStayRecord::customerId,
            Collectors.mapping(record -> new Customer.StayRecord(
                record.stayId(), record.checkInAt(), record.actualCheckOutAt()),
                Collectors.toList())));
    customers.forEach(customer -> {
      List<Customer.StayRecord> history = histories.getOrDefault(customer.getId(), List.of());
      customer.setStayHistory(history);
      customer.setHotelStayCount(history.size());
    });
  }

  @Transactional
  public CustomerResult createOrUpdate(
      String name,
      String phone,
      String proofType,
      String proofNumber,
      String proofTypeOther,
      String address) {
    validateIdProof(proofType, proofNumber, proofTypeOther);
    String normalizedProofTypeOther = "other".equals(proofType) ? proofTypeOther.trim() : null;
    UUID tid = tenant.tenantId();
    var existing = repository.findByTenantIdAndPhone(tid, phone);
    if (existing.isPresent()) {
      Customer customer = existing.get();
      customer.updateProfile(
          name, phone, proofType, proofNumber, normalizedProofTypeOther, address);
      return new CustomerResult(customer, false);
    }
    return new CustomerResult(
        repository.save(
            new Customer(
                tid, name, phone, proofType, proofNumber, normalizedProofTypeOther, address)),
        true);
  }

  @Transactional
  public Customer updateProfile(
      UUID customerId,
      String name,
      String phone,
      String proofType,
      String proofNumber,
      String proofTypeOther,
      String address) {
    validateIdProof(proofType, proofNumber, proofTypeOther);
    UUID tenantId = tenant.tenantId();
    Customer customer = repository.findByTenantIdAndId(tenantId, customerId)
        .orElseThrow(() -> new java.util.NoSuchElementException("Guest not found"));
    long stayCount = stays.findCustomerStayStats(tenantId, List.of(customerId)).stream()
        .mapToLong(StayLookupPort.CustomerStayStats::stayCount)
        .findFirst()
        .orElse(0L);
    if (stayCount > 0) {
      throw new IllegalStateException("Guest profiles with recorded stays are read-only");
    }
    if (repository.existsByTenantIdAndPhoneAndIdNot(tenantId, phone, customerId)) {
      throw new IllegalArgumentException("Phone number is already used by another guest");
    }
    String normalizedProofTypeOther = "other".equals(proofType) ? proofTypeOther.trim() : null;
    customer.updateProfile(
        name, phone, proofType, proofNumber, normalizedProofTypeOther, address);
    return customer;
  }

  private void validateIdProof(String proofType, String proofNumber, String proofTypeOther) {
    if (proofType == null || proofType.isBlank()) {
      if (proofNumber != null && !proofNumber.isBlank()) {
        throw new IllegalArgumentException("Select an ID proof type when an ID number is provided");
      }
      return;
    }
    if (!Set.of("aadhaar", "passport", "voter_id", "driving_license", "other")
        .contains(proofType)) {
      throw new IllegalArgumentException("Invalid ID proof type");
    }
    if (proofNumber == null || proofNumber.isBlank()) {
      throw new IllegalArgumentException("ID proof number is required when an ID type is selected");
    }
    if (proofNumber.length() > 50) {
      throw new IllegalArgumentException("ID proof number must be at most 50 characters");
    }
    if ("other".equals(proofType)) {
      if (proofTypeOther == null || proofTypeOther.isBlank()) {
        throw new IllegalArgumentException("Specify the ID type when Other is selected");
      }
      if (proofTypeOther.trim().length() > 100) {
        throw new IllegalArgumentException("Specified ID type must be at most 100 characters");
      }
    }
  }

  public record CustomerResult(Customer customer, boolean isNew) {}
}
