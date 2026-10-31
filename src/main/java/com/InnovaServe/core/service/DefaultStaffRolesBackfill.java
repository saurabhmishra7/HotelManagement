package com.InnovaServe.core.service;

import com.InnovaServe.core.repository.TenantRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DefaultStaffRolesBackfill implements ApplicationRunner {
  private final TenantRepository tenants;
  private final DefaultStaffRoleSeeder seeder;

  public DefaultStaffRolesBackfill(TenantRepository tenants, DefaultStaffRoleSeeder seeder) {
    this.tenants = tenants;
    this.seeder = seeder;
  }

  @Override
  public void run(ApplicationArguments args) {
    tenants.findAllTenantIds().forEach(seeder::ensureForTenant);
  }
}
