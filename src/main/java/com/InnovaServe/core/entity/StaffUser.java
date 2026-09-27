package com.InnovaServe.core.entity;

import jakarta.persistence.*;import java.time.LocalDateTime;import java.util.UUID;
@Entity @Table(name="users",schema="core",uniqueConstraints=@UniqueConstraint(name="uq_users_tenant_phone",columnNames={"tenant_id","phone"}))
public class StaffUser {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(name="tenant_id",nullable=false) private UUID tenantId;
 @Column(nullable=false,length=100) private String name;
 @Column(nullable=false,length=15) private String phone;
 @Column(length=150) private String email;
 @Column(name="password_hash",nullable=false,columnDefinition="text") private String passwordHash;
 @Column(name="pin_hash",columnDefinition="text") private String pinHash;
 @Column(name="role_id") private UUID roleId;
 @Column(nullable=false) private boolean active=true;
 @Column(name="created_at",insertable=false,updatable=false) private LocalDateTime createdAt;
 protected StaffUser(){}
 public StaffUser(UUID tenantId,String name,String phone,String email,String passwordHash,String pinHash,UUID roleId){this.tenantId=tenantId;this.name=name;this.phone=phone;this.email=email;this.passwordHash=passwordHash;this.pinHash=pinHash;this.roleId=roleId;}
 public UUID getId(){return id;}public UUID getTenantId(){return tenantId;}public String getName(){return name;}public String getPhone(){return phone;}public String getEmail(){return email;}public UUID getRoleId(){return roleId;}public boolean isActive(){return active;}public String getPasswordHash(){return passwordHash;}public String getPinHash(){return pinHash;}
 public void update(String name,UUID roleId,Boolean active){if(name!=null)this.name=name;if(roleId!=null)this.roleId=roleId;if(active!=null)this.active=active;}
}
