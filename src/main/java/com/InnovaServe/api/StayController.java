package com.InnovaServe.api;
import com.InnovaServe.stay.service.StayService;import com.InnovaServe.stay.entity.*;import org.springframework.web.bind.annotation.*;import java.util.*;import java.math.*;
@RestController @RequestMapping("/api/v1")public class StayController{
 private final StayService service;public StayController(StayService service){this.service=service;}
 @GetMapping("/rooms")public List<Room> rooms(){return service.rooms();}@GetMapping("/rooms/{id}")public Room room(@PathVariable UUID id){return service.room(id);}
 @PatchMapping("/rooms/{id}/status")public Room roomStatus(@PathVariable UUID id,@RequestBody StatusRequest r){if(!Set.of("vacant","occupied","dirty","clean").contains(r.status()))throw new IllegalArgumentException("Invalid room status");return service.setRoomStatus(id,r.status());}
 @PostMapping("/stays")public Map<String,Object> checkIn(@RequestBody StayService.CheckIn request){Stay s=service.checkIn(request);return Map.of("stay_id",s.getId(),"account_id",s.getAccountId());}
 @GetMapping("/stays/active")public Object active(@RequestParam("room_number")String roomNumber){return service.activeStay(roomNumber);}
 @GetMapping("/stays/{id}")public Map<String,Object> stay(@PathVariable UUID id){return Map.of("stay",service.getStay(id),"charges",service.charges(id));}
 @PostMapping("/stays/{id}/charges")public StayCharge charge(@PathVariable UUID id,@RequestBody ChargeRequest r){return service.addCharge(id,r.description(),r.amount());}
 @PostMapping("/stays/{id}/checkout")public Map<String,Object> checkout(@PathVariable UUID id){return service.checkout(id);}
 @PostMapping("/stays/{id}/confirm-checkout")public Map<String,String> confirmCheckout(@PathVariable UUID id){service.confirmCheckout(id);return Map.of("status","checked_out");}
 @GetMapping("/stays/{id}/form-c")public FormCSubmission formC(@PathVariable UUID id){return service.formC(id);}
 @PostMapping("/stays/{id}/form-c/submit")public FormCSubmission submit(@PathVariable UUID id,@RequestBody FormCRequest r){return service.submitFormC(id,r.referenceNumber());}
 public record StatusRequest(String status){}public record ChargeRequest(String description,BigDecimal amount){}public record FormCRequest(String referenceNumber){}
}
