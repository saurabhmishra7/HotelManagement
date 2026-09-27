package com.InnovaServe.stay.entity;
import com.InnovaServe.core.entity.TenantEntity;import jakarta.persistence.*;import java.math.BigDecimal;import java.util.UUID;
@Entity @Table(name="room",schema="stay",uniqueConstraints=@UniqueConstraint(name="uq_room_tenant_number",columnNames={"tenant_id","room_number"}))public class Room extends TenantEntity{
 @Column(name="room_number",nullable=false,length=10)private String roomNumber;@Column(name="room_type",nullable=false,length=50)private String roomType;@Column(length=10)private String floor;@Column(name="base_tariff",nullable=false,precision=10,scale=2)private BigDecimal baseTariff;@Column(nullable=false,length=10)private String status;
 protected Room(){}public Room(UUID t,String number,String type,String floor,BigDecimal tariff,String status){super(t);roomNumber=number;roomType=type;this.floor=floor;baseTariff=tariff;this.status=status;}public String getRoomNumber(){return roomNumber;}public String getRoomType(){return roomType;}public String getFloor(){return floor;}public BigDecimal getBaseTariff(){return baseTariff;}public String getStatus(){return status;}public void setStatus(String s){status=s;}
}
