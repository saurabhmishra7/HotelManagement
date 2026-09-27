package com.InnovaServe.core.service;

import com.InnovaServe.core.entity.Customer;
import com.InnovaServe.core.repository.CustomerRepository;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@Transactional(readOnly=true)
public class CustomerService {
    private final CustomerRepository repository; private final TenantContext tenant;
    public CustomerService(CustomerRepository repository,TenantContext tenant){this.repository=repository;this.tenant=tenant;}
    public Page<Customer> list(int page,int size){return repository.findAllByTenantId(tenant.tenantId(),PageRequest.of(Math.max(0,page),Math.clamp(size,1,100),Sort.by("name").ascending()));}
    public Customer findByPhone(String phone){return repository.findByTenantIdAndPhone(tenant.tenantId(),phone).orElse(null);}
    @Transactional public CustomerResult createOrUpdate(String name,String phone,String proofType,String proofNumber,String address){
        UUID tid=tenant.tenantId();
        var existing=repository.findByTenantIdAndPhone(tid,phone);
        if(existing.isPresent()){Customer customer=existing.get();customer.update(name,proofType,proofNumber,address);return new CustomerResult(customer,false);}
        return new CustomerResult(repository.save(new Customer(tid,name,phone,proofType,proofNumber,address)),true);
    }
    public record CustomerResult(Customer customer,boolean isNew){}
}
