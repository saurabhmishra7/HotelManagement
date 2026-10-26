package com.InnovaServe.restaurant.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.InnovaServe.core.repository.*;
import com.InnovaServe.core.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

/** Run only against a disposable localhost database. Never reads application datasource settings. */
@EnabledIfEnvironmentVariable(named="PRINTER_TEST_DB_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:55446/.*")
class PrinterQueueIntegrationTest {
  static JdbcTemplate db;
  static TransactionTemplate tx;
  PrinterService service;
  UUID t,user,order,device;
  @BeforeAll static void migrate() {
    String url=System.getenv("PRINTER_TEST_DB_URL");
    Flyway.configure().dataSource(url,"postgres","").locations("classpath:db/migration").load().migrate();
    var ds=new DriverManagerDataSource(url,"postgres","");
    db=new JdbcTemplate(ds); tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
  }
  @BeforeEach void setup() {
    t=UUID.randomUUID(); user=UUID.randomUUID(); order=UUID.randomUUID();
    var modules=mock(ModuleEntitlementService.class);
    when(modules.isActive(any(),any())).thenReturn(true);
    var stations=mock(RestaurantStationService.class);
    when(stations.barAvailable(any())).thenReturn(true);
    service=new PrinterService(db,new TenantContext(),new ObjectMapper(),mock(EntityManager.class),
        mock(StaffUserRepository.class),mock(StaffRoleRepository.class),modules,stations);
    db.update("INSERT INTO core.tenant(id,name,tenant_code,restaurant_service_mode) VALUES(?,'Printer test',?,'both')",t,t.toString().substring(0,12));
    db.update("INSERT INTO core.users(id,tenant_id,name,phone,password_hash) VALUES(?,?,'Operator','9000000000','test-only')",user,t);
    db.update("INSERT INTO restaurant.restaurant_order(id,tenant_id,order_type,status,order_source,confirmation_status) VALUES(?,?,'takeaway','open','staff','not_required')",order,t);
    device=as(t,()->(UUID)service.register(Map.of("label","Test","connection_type","webusb","usb_vendor_id",123,"usb_product_id",456)).get("id"));
    as(t,()->{service.tested(device);service.route("kitchen_kot",device);return null;});
  }
  <T> T as(UUID tenant,Supplier<T> body) {
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        Map.of("tenant_id",tenant,"user_id",user),null,List.of(new SimpleGrantedAuthority("PERM_PRINTER_OPERATOR"),new SimpleGrantedAuthority("PERM_BILLING_READ"))));
    try { return tx.execute(s->body.get()); } finally { SecurityContextHolder.clearContext(); }
  }
  UUID batch(String station,short number) {
    UUID batch=UUID.randomUUID();
    db.update("INSERT INTO restaurant.kot_batch(id,tenant_id,order_id,station,batch_number) VALUES(?,?,?,?,?)",batch,t,order,station,number);
    return batch;
  }
  PrintTicketsCreated event(UUID batch,String station,String name) {
    return new PrintTicketsCreated(t,order,List.of(Map.of("id",batch,"station",station,"batch_number",1,
        "items",List.of(Map.of("name",name,"quantity",1)))));
  }
  void enqueue(UUID batch,String name) { as(t,()->{service.enqueue(event(batch,"kitchen",name));return null;}); }
  @SuppressWarnings("unchecked")
  List<Map<String,Object>> jobs(Map<String,Object> result) { return (List<Map<String,Object>>)result.get("jobs"); }

  @Test void newBatchesAreIndependentAndEnqueueIsIdempotent() {
    UUID first=batch("kitchen",(short)1), second=batch("kitchen",(short)2);
    enqueue(first,"Roti"); enqueue(first,"Roti"); enqueue(second,"Daal");
    assertEquals(2,db.queryForObject("SELECT count(*) FROM restaurant.printer_job WHERE tenant_id=?",Integer.class,t));
    String payload=db.queryForObject("SELECT snapshot::text FROM restaurant.printer_job WHERE batch_id=?",String.class,second);
    assertTrue(payload.contains("Daal")); assertFalse(payload.contains("Roti"));
  }
  @Test void transactionRollbackDoesNotLeaveAnOrphanPrintJob() {
    UUID b=batch("kitchen",(short)1);
    assertThrows(IllegalStateException.class,()->as(t,()->{service.enqueue(event(b,"kitchen","Soup"));throw new IllegalStateException("rollback");}));
    assertEquals(0,db.queryForObject("SELECT count(*) FROM restaurant.printer_job WHERE batch_id=?",Integer.class,b));
  }
  @Test void kitchenDisplayModeDoesNotEnqueueAndUnassignedRoutesWait() {
    db.update("UPDATE core.tenant SET restaurant_service_mode='kitchen_display' WHERE id=?",t);
    enqueue(batch("kitchen",(short)1),"Soup");
    assertEquals(0,db.queryForObject("SELECT count(*) FROM restaurant.printer_job WHERE tenant_id=?",Integer.class,t));
    db.update("UPDATE core.tenant SET restaurant_service_mode='both' WHERE id=?",t);
    as(t,()->{service.route("kitchen_kot",null);return null;});
    enqueue(batch("kitchen",(short)2),"Soup");
    assertTrue(jobs(as(t,()->service.pending(device,null))).isEmpty());
    as(t,()->{service.route("kitchen_kot",device);return null;});
    assertEquals(1,jobs(as(t,()->service.pending(device,null))).size());
  }
  @Test void twoListenersCannotClaimTwoOutstandingJobsForOneDevice() throws Exception {
    enqueue(batch("kitchen",(short)1),"First"); enqueue(batch("kitchen",(short)2),"Second");
    try(var pool=Executors.newFixedThreadPool(2)) {
      var start=new CountDownLatch(1);
      Callable<Integer> call=()->{start.await();return jobs(as(t,()->service.pending(device,null))).size();};
      var a=pool.submit(call); var b=pool.submit(call); start.countDown();
      assertEquals(1,a.get(10,TimeUnit.SECONDS)+b.get(10,TimeUnit.SECONDS));
    }
  }
  @Test void lostAcknowledgementRequiresReviewAndRejectsOldAttemptAfterRetry() {
    enqueue(batch("kitchen",(short)1),"Soup");
    var first=jobs(as(t,()->service.pending(device,null))).getFirst();
    db.update("UPDATE restaurant.printer_job SET claimed_at=now()-interval '3 minutes' WHERE id=?",first.get("id"));
    assertTrue(jobs(as(t,()->service.pending(device,null))).isEmpty());
    assertEquals("uncertain",db.queryForObject("SELECT status FROM restaurant.printer_job WHERE id=?",String.class,first.get("id")));
    as(t,()->{service.review((UUID)first.get("id"),true);return null;});
    var next=jobs(as(t,()->service.pending(device,null))).getFirst();
    assertNotEquals(first.get("claim_id"),next.get("claim_id"));
    assertThrows(IllegalStateException.class,()->as(t,()->{service.acknowledge(device,(UUID)first.get("id"),null,
        Map.of("claim_id",first.get("claim_id").toString(),"status","printed"));return null;}));
  }
  @Test void acknowledgementIsIdempotentAndAnotherTenantCannotReadOrClaimDevice() {
    UUID batch=batch("kitchen",(short)1);enqueue(batch,"Soup");
    var job=jobs(as(t,()->service.pending(device,null))).getFirst();
    var body=Map.<String,Object>of("status","printed","claim_id",job.get("claim_id").toString());
    as(t,()->{service.acknowledge(device,(UUID)job.get("id"),null,body);return null;});
    as(t,()->{service.acknowledge(device,(UUID)job.get("id"),null,body);return null;});
    assertNotNull(db.queryForObject("SELECT printed_at FROM restaurant.kot_batch WHERE id=?",java.sql.Timestamp.class,batch));
    assertThrows(NoSuchElementException.class,()->as(UUID.randomUUID(),()->service.pending(device,null)));
    assertThrows(org.springframework.security.access.AccessDeniedException.class,()->as(t,()->service.pending(device,"wrong-key")));
  }
  @Test void restaurantAndBarShareOnePrinterWithoutCrossPrinting() {
    as(t,()->{service.route("bar_kot",device);return null;});
    UUID b=batch("bar",(short)1);
    as(t,()->{service.enqueue(event(b,"bar","Drink"));return null;});
    var job=jobs(as(t,()->service.pending(device,null))).getFirst();
    assertEquals("bar",((Map<?,?>)job.get("snapshot")).get("station"));
    as(t,()->{service.update(device,Map.of("active",false));return null;});
    assertEquals("uncertain",db.queryForObject("SELECT status FROM restaurant.printer_job WHERE id=?",String.class,job.get("id")));
    assertThrows(org.springframework.security.access.AccessDeniedException.class,()->as(t,()->service.pending(device,null)));
  }
}
