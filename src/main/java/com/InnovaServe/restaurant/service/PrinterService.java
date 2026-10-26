package com.InnovaServe.restaurant.service;

import com.InnovaServe.core.repository.*;
import com.InnovaServe.core.security.*;
import com.InnovaServe.core.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional
public class PrinterService {
  private final JdbcTemplate db;
  private final TenantContext tenant;
  private final ObjectMapper json;
  private final EntityManager em;
  private final StaffUserRepository users;
  private final StaffRoleRepository roles;
  private final ModuleEntitlementService modules;
  private final RestaurantStationService stations;
  private static final Set<String> PURPOSES = Set.of("kitchen_kot","bar_kot","restaurant_bill","hotel_reception");
  private static final String DEVICE_COLUMNS = "d.id,d.tenant_id,d.label,d.connection_type,d.usb_vendor_id,d.usb_product_id,d.usb_serial,d.network_ip_address,d.printer_brand,d.printer_model,d.paper_width,d.active,d.tested_at,d.paired_at,d.last_seen_at";

  public PrinterService(JdbcTemplate db, TenantContext tenant, ObjectMapper json, EntityManager em,
      StaffUserRepository users, StaffRoleRepository roles, ModuleEntitlementService modules,
      RestaurantStationService stations) {
    this.db=db; this.tenant=tenant; this.json=json; this.em=em;
    this.users=users; this.roles=roles; this.modules=modules; this.stations=stations;
  }

  private void operator() {
    var auth=SecurityContextHolder.getContext().getAuthentication();
    if(auth==null || auth.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("PERM_PRINTER_OPERATOR")))
      throw new AccessDeniedException("Printer Operator access is required");
  }
  private void scope(UUID t) {
    if(!modules.isActive(t,ModuleType.RESTAURANT) && !modules.isActive(t,ModuleType.STAY))
      throw new AccessDeniedException("An active Restaurant or Stay subscription is required");
  }
  private void purpose(UUID t,String p) {
    if(!PURPOSES.contains(p)) throw new IllegalArgumentException("Choose a printer purpose");
    modules.requireActive(t,p.equals("hotel_reception")?ModuleType.STAY:ModuleType.RESTAURANT);
    if(p.equals("bar_kot") && !stations.barAvailable(t)) throw new AccessDeniedException("Bar is not enabled");
  }
  public Map<String,Object> list() { operator(); scope(tenant.tenantId()); return overview(tenant.tenantId()); }

  /** Only called through a platform-authenticated controller for support visibility. */
  public Map<String,Object> overview(UUID t) {
    expire(t);
    var devices=db.queryForList("SELECT "+DEVICE_COLUMNS+"""
        ,CASE WHEN EXISTS(SELECT 1 FROM restaurant.printer_job j WHERE j.device_id=d.id AND j.status='uncertain')
           THEN 'uncertain' WHEN d.active AND d.last_seen_at > now()-interval '35 seconds'
           THEN 'connected' ELSE 'disconnected' END AS status
        FROM restaurant.printer_device d WHERE d.tenant_id=? ORDER BY d.paired_at
        """,t);
    var routes=db.queryForList("SELECT purpose,device_id FROM restaurant.printer_route WHERE tenant_id=?",t);
    var jobs=db.queryForList("""
        SELECT id,purpose,device_id,status,created_at,completed_at,detail,batch_id,
          snapshot->>'title' AS title FROM restaurant.printer_job
        WHERE tenant_id=? ORDER BY created_at DESC,id DESC LIMIT 100
        """,t);
    return Map.of("devices",devices,"routes",routes,"jobs",jobs);
  }

  public Map<String,Object> register(Map<String,Object> input) {
    operator(); UUID t=tenant.tenantId(); scope(t);
    String type=text(input,"connection_type",20,true);
    if(!Set.of("webusb","network_http","agent_bridge").contains(type)) throw new IllegalArgumentException("Unknown printer connection");
    Integer vendor=null,product=null;
    if(type.equals("webusb")) {
      vendor=integer(input,"usb_vendor_id",0,65535); product=integer(input,"usb_product_id",0,65535);
    }
    String address=text(input,"network_ip_address",255,false);
    if(type.equals("network_http")) validateNetworkAddress(address);
    String brand=text(input,"printer_brand",50,false), model=text(input,"printer_model",100,false);
    if(type.equals("network_http")) {
      var matches=compatibility(brand,model);
      if(matches.stream().anyMatch(r->!r.get("connection_type").equals("network_http_eposprint")
          || r.get("verified_status").equals("known_incompatible")))
        throw new IllegalArgumentException("This model needs the Print Agent; direct ePOS printing is not supported");
    }
    UUID id=UUID.randomUUID();
    db.update("""
        INSERT INTO restaurant.printer_device
        (id,tenant_id,label,connection_type,usb_vendor_id,usb_product_id,usb_serial,network_ip_address,
         printer_brand,printer_model,paper_width,paired_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
        """,id,t,text(input,"label",100,true),type,vendor,product,text(input,"usb_serial",200,false),
        address,brand,model,paper(input),tenant.userId());
    return Map.of("id",id);
  }
  private int paper(Map<String,Object> input) {
    int width=input.containsKey("paper_width")?integer(input,"paper_width",58,80):80;
    if(width!=58 && width!=80) throw new IllegalArgumentException("Choose 58 or 80 mm paper");
    return width;
  }
  private Map<String,Object> lock(UUID t,UUID id) {
    var rows=db.queryForList("SELECT * FROM restaurant.printer_device WHERE tenant_id=? AND id=? FOR UPDATE",t,id);
    if(rows.isEmpty()) throw new NoSuchElementException("Printer not found");
    return rows.getFirst();
  }
  public void update(UUID id,Map<String,Object> input) {
    operator(); UUID t=tenant.tenantId(); scope(t);
    if(input.containsKey("purpose")) route(String.valueOf(input.get("purpose")),id);
    lock(t,id);
    expire(t);
    if(Boolean.FALSE.equals(input.get("active"))) {
      db.update("UPDATE restaurant.printer_device SET active=false,key_hash=NULL WHERE tenant_id=? AND id=?",t,id);
      db.update("DELETE FROM restaurant.printer_route WHERE tenant_id=? AND device_id=?",t,id);
      db.update("UPDATE restaurant.printer_job SET status='uncertain',detail='Printer deactivated during dispatch' WHERE tenant_id=? AND device_id=? AND status='dispatching'",t,id);
    }
    if(input.containsKey("label")) db.update("UPDATE restaurant.printer_device SET label=? WHERE tenant_id=? AND id=?",text(input,"label",100,true),t,id);
  }
  public void route(String p,UUID id) {
    operator(); UUID t=tenant.tenantId(); purpose(t,p);
    // Serialize route changes (including unassignment) independently of device changes.
    db.queryForList("SELECT id FROM core.tenant WHERE id=? FOR UPDATE",t);
    if(id==null) { db.update("DELETE FROM restaurant.printer_route WHERE tenant_id=? AND purpose=?",t,p); return; }
    var device=lock(t,id);
    if(!Boolean.TRUE.equals(device.get("active"))) throw new IllegalArgumentException("Choose an active printer");
    if(device.get("tested_at")==null) throw new IllegalStateException("Print and confirm a test slip before assigning this printer");
    db.update("""
        INSERT INTO restaurant.printer_route(tenant_id,purpose,device_id) VALUES(?,?,?)
        ON CONFLICT(tenant_id,purpose) DO UPDATE SET device_id=excluded.device_id
        """,t,p,id);
  }

  private Map<String,Object> authenticate(UUID id,String key) {
    UUID t;
    if(key!=null && !key.isBlank()) {
      if(key.length()>200) throw new AccessDeniedException("Invalid printer key");
      var matches=db.queryForList("SELECT tenant_id FROM restaurant.printer_device WHERE id=? AND key_hash=? AND active=true",id,hash(key));
      if(matches.isEmpty()) throw new AccessDeniedException("Printer key revoked or invalid");
      t=(UUID)matches.getFirst().get("tenant_id");
    } else { operator(); t=tenant.tenantId(); }
    var d=lock(t,id);
    if(!Boolean.TRUE.equals(d.get("active"))) throw new AccessDeniedException("Printer is deactivated");
    if(key!=null && !key.isBlank()) {
      if(!Objects.equals(d.get("key_hash"),hash(key))) throw new AccessDeniedException("Printer key revoked");
      var user=users.findByTenantIdAndId(t,(UUID)d.get("paired_by")).filter(u->u.isActive())
          .orElseThrow(()->new AccessDeniedException("Printer operator is inactive"));
      if(!StaffAccess.permissions(roles.findAllByTenantIdAndIdIn(t,user.getRoleIds())).contains("PRINTER_OPERATOR"))
        throw new AccessDeniedException("Printer operator access was removed");
    }
    scope(t);
    return d;
  }
  public Map<String,Object> pending(UUID id,String key) {
    var d=authenticate(id,key); UUID t=(UUID)d.get("tenant_id"); expire(t);
    db.update("UPDATE restaurant.printer_device SET last_seen_at=now() WHERE id=?",id);
    if(d.get("tested_at")==null) return Map.of("jobs",List.of(),"message","Confirm a test slip first");
    if(Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM restaurant.printer_job WHERE device_id=? AND status IN ('dispatching','uncertain'))",Boolean.class,id)))
      return Map.of("jobs",List.of(),"message","Waiting for acknowledgement or review of an uncertain ticket");
    var allowed=new ArrayList<String>();
    for(String p:PURPOSES) {
      if(p.equals("hotel_reception") ? modules.isActive(t,ModuleType.STAY) : modules.isActive(t,ModuleType.RESTAURANT)) {
        if(!p.equals("bar_kot") || stations.barAvailable(t)) allowed.add(p);
      }
    }
    String mode=db.queryForObject("SELECT restaurant_service_mode FROM core.tenant WHERE id=?",String.class,t);
    if("kitchen_display".equals(mode)) { allowed.remove("kitchen_kot"); allowed.remove("bar_kot"); }
    // Device lock serializes claims from tabs/PCs. Job lock also covers a concurrent route reassignment.
    var candidates=db.queryForList("""
        SELECT j.id,j.snapshot::text AS snapshot FROM restaurant.printer_job j
        JOIN restaurant.printer_route r ON r.tenant_id=j.tenant_id AND r.purpose=j.purpose
        WHERE j.tenant_id=? AND r.device_id=? AND j.status='pending'
        AND j.purpose=ANY(?::text[]) ORDER BY j.created_at,j.id LIMIT 1 FOR UPDATE OF j SKIP LOCKED
        """,t,id,"{"+String.join(",",allowed)+"}");
    if(candidates.isEmpty()) return Map.of("jobs",List.of());
    var job=candidates.getFirst(); UUID claim=UUID.randomUUID();
    db.update("UPDATE restaurant.printer_job SET status='dispatching',device_id=?,claim_id=?,claimed_at=now(),detail=NULL WHERE id=?",id,claim,job.get("id"));
    return Map.of("jobs",List.of(Map.of("id",job.get("id"),"claim_id",claim,"snapshot",parse(job.get("snapshot").toString()),"paper_width",d.get("paper_width"))));
  }
  public void acknowledge(UUID deviceId,UUID jobId,String key,Map<String,Object> body) {
    var d=authenticate(deviceId,key); UUID t=(UUID)d.get("tenant_id");
    String status=text(body,"status",20,true);
    if(!Set.of("printed","uncertain").contains(status)) throw new IllegalArgumentException("Report printed or uncertain");
    UUID claim=UUID.fromString(text(body,"claim_id",36,true));
    var rows=db.queryForList("SELECT * FROM restaurant.printer_job WHERE tenant_id=? AND device_id=? AND id=? FOR UPDATE",t,deviceId,jobId);
    if(rows.isEmpty()) throw new NoSuchElementException("Print job not found");
    var job=rows.getFirst();
    if(!claim.equals(job.get("claim_id"))) throw new IllegalStateException("This print attempt has been replaced");
    if(job.get("status").equals(status)) return; // Retry ACK after a lost response, never retry physical output.
    if(!job.get("status").equals("dispatching")) throw new IllegalStateException("Ticket already needs review; refresh the queue");
    db.update("UPDATE restaurant.printer_job SET status=?,detail=?,completed_at=now() WHERE id=?",status,text(body,"detail",500,false),jobId);
    if(status.equals("printed") && job.get("batch_id")!=null)
      db.update("UPDATE restaurant.kot_batch SET printed_at=COALESCE(printed_at,now()) WHERE tenant_id=? AND id=?",t,job.get("batch_id"));
  }
  public void review(UUID jobId,boolean retry) {
    operator(); UUID t=tenant.tenantId(); scope(t); expire(t);
    int changed=db.update("""
        UPDATE restaurant.printer_job SET status=?,detail=?,claim_id=NULL,
        device_id=CASE WHEN ? THEN NULL ELSE device_id END,completed_at=now()
        WHERE tenant_id=? AND id=? AND status='uncertain'
        """,retry?"pending":"resolved",retry?"Staff checked paper and requested retry":"Staff checked paper; no reprint needed",retry,t,jobId);
    if(changed!=1) throw new IllegalStateException("Only an uncertain ticket can be reviewed");
  }
  public void tested(UUID id) {
    operator(); UUID t=tenant.tenantId(); scope(t); lock(t,id);
    db.update("UPDATE restaurant.printer_device SET tested_at=now() WHERE tenant_id=? AND id=? AND active=true",t,id);
  }
  public void agentTested(UUID id,String key) {
    authenticate(id,key);
    db.update("UPDATE restaurant.printer_device SET tested_at=now(),last_seen_at=now() WHERE id=?",id);
  }
  public String agentKey(UUID id) {
    operator(); UUID t=tenant.tenantId(); scope(t); var d=lock(t,id);
    if(!Boolean.TRUE.equals(d.get("active")) || !"agent_bridge".equals(d.get("connection_type")))
      throw new IllegalArgumentException("Choose an active agent printer");
    byte[] bytes=new byte[32]; new SecureRandom().nextBytes(bytes);
    String key=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    db.update("UPDATE restaurant.printer_device SET key_hash=?,paired_by=? WHERE id=?",hash(key),tenant.userId(),id);
    return key;
  }
  private void expire(UUID t) {
    db.update("""
        UPDATE restaurant.printer_job SET status='uncertain',detail='No acknowledgement within two minutes; check the printer before retrying'
        WHERE tenant_id=? AND status='dispatching' AND claimed_at < now()-interval '2 minutes'
        """,t);
  }

  @EventListener
  @Transactional(propagation=Propagation.MANDATORY)
  public void enqueue(PrintTicketsCreated event) {
    String mode=db.queryForObject("SELECT restaurant_service_mode FROM core.tenant WHERE id=?",String.class,event.tenantId());
    if(!Set.of("thermal_printer","both").contains(mode)) return;
    em.flush(); // New JPA batch rows must exist before the JDBC FK insert; same transaction.
    var location=db.queryForMap("""
        SELECT t.name AS property,o.order_type,dt.table_number,rm.room_number
        FROM restaurant.restaurant_order o JOIN core.tenant t ON t.id=o.tenant_id
        LEFT JOIN restaurant.dining_table dt ON dt.id=o.table_id AND dt.tenant_id=o.tenant_id
        LEFT JOIN stay.stay s ON s.id=o.stay_id AND s.tenant_id=o.tenant_id
        LEFT JOIN stay.room rm ON rm.id=s.room_id AND rm.tenant_id=o.tenant_id
        WHERE o.id=? AND o.tenant_id=?
        """,event.orderId(),event.tenantId());
    for(var ticket:event.tickets()) {
      var snapshot=new LinkedHashMap<String,Object>(ticket);
      snapshot.putAll(location); snapshot.put("order_id",event.orderId());
      snapshot.put("title","KOT · "+ticket.get("station")+" · "+ticket.get("batch_number"));
      snapshot.put("created_at",java.time.Instant.now().toString());
      String p="kitchen".equals(ticket.get("station"))?"kitchen_kot":"bar_kot";
      db.update("""
          INSERT INTO restaurant.printer_job(id,tenant_id,purpose,batch_id,snapshot)
          VALUES(?,?,?,?,?::jsonb) ON CONFLICT(batch_id) DO NOTHING
          """,UUID.randomUUID(),event.tenantId(),p,ticket.get("id"),write(snapshot));
    }
  }

  public List<Map<String,Object>> compatibility(String brand,String model) {
    return db.queryForList("""
        SELECT id,brand,model,connection_type,verified_status,notes,updated_at FROM platform.printer_compatibility
        WHERE (?='' OR lower(brand)=lower(?)) AND (?='' OR lower(model)=lower(?))
        ORDER BY brand,model LIMIT 500
        """,Objects.toString(brand,""),Objects.toString(brand,""),Objects.toString(model,""),Objects.toString(model,""));
  }
  public void saveCompatibility(UUID id,UUID admin,Map<String,Object> input) {
    String type=text(input,"connection_type",30,true),status=text(input,"verified_status",20,true);
    if(!Set.of("usb_webusb","network_http_eposprint","network_http_other","unsupported").contains(type)
       || !Set.of("verified","community_reported","untested","known_incompatible").contains(status))
      throw new IllegalArgumentException("Choose a valid compatibility type and verification status");
    if(id==null) db.update("""
        INSERT INTO platform.printer_compatibility(id,brand,model,connection_type,verified_status,notes,added_by)
        VALUES(?,?,?,?,?,?,?)
        """,UUID.randomUUID(),text(input,"brand",50,true),text(input,"model",100,true),type,status,text(input,"notes",4000,false),admin);
    else if(db.update("""
        UPDATE platform.printer_compatibility SET brand=?,model=?,connection_type=?,verified_status=?,notes=?,updated_at=now() WHERE id=?
        """,text(input,"brand",50,true),text(input,"model",100,true),type,status,text(input,"notes",4000,false),id)!=1)
      throw new NoSuchElementException("Printer model not found");
  }
  public Map<String,Object> receipt(UUID invoiceId, UUID requestId) {
    UUID t=tenant.tenantId();
    var invoiceRows=db.queryForList("SELECT i.*,t.name AS property,t.gstin,t.address FROM core.invoice i JOIN core.tenant t ON t.id=i.tenant_id WHERE i.tenant_id=? AND i.id=?",t,invoiceId);
    if(invoiceRows.isEmpty()) throw new NoSuchElementException("Invoice not found");
    var invoice=invoiceRows.getFirst();
    String source=invoice.get("source_module").toString();
    if(!Set.of("restaurant","stay").contains(source) || "draft".equals(invoice.get("status")))
      throw new IllegalArgumentException("Only finalized Restaurant or Stay invoices can be printed here");
    var auth=SecurityContextHolder.getContext().getAuthentication();
    boolean canRead=auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("PERM_BILLING_READ"));
    boolean canBill=auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("PERM_BILL_CREATE"));
    if(!canRead && !(source.equals("restaurant") && canBill)) throw new AccessDeniedException("Invoice access is required");
    String p=source.equals("restaurant")?"restaurant_bill":"hotel_reception";
    purpose(t,p);
    var snapshot=new LinkedHashMap<String,Object>(invoice);
    snapshot.put("title","Invoice "+invoice.get("invoice_number"));
    snapshot.put("created_at",java.time.Instant.now().toString());
    var paid=db.queryForObject("SELECT COALESCE(sum(amount),0) FROM core.payment WHERE tenant_id=? AND invoice_id=?",java.math.BigDecimal.class,t,invoiceId);
    var credits=db.queryForObject("SELECT COALESCE(sum(amount),0) FROM core.credit_note WHERE tenant_id=? AND original_invoice_id=?",java.math.BigDecimal.class,t,invoiceId);
    snapshot.put("paid_total",paid); snapshot.put("credit_total",credits);
    snapshot.put("amount_due",((java.math.BigDecimal)invoice.get("total_amount")).subtract(paid).subtract(credits).max(java.math.BigDecimal.ZERO));
    snapshot.put("items",db.queryForList("SELECT description,quantity,unit_price,tax_amount,line_total FROM core.invoice_line_item WHERE tenant_id=? AND invoice_id=? ORDER BY id",t,invoiceId));
    db.update("""
        INSERT INTO restaurant.printer_job(id,tenant_id,purpose,invoice_id,snapshot)
        VALUES(?,?,?,?,?::jsonb) ON CONFLICT(id) DO NOTHING
        """,requestId,t,p,invoiceId,write(snapshot));
    return Map.of("job_id",requestId,"purpose",p);
  }
  static String text(Map<String,Object> m,String k,int limit,boolean required) {
    String s=Objects.toString(m.get(k),"").trim();
    if((required && s.isEmpty()) || s.length()>limit) throw new IllegalArgumentException("Enter a valid "+k.replace('_',' '));
    return s;
  }
  static int integer(Map<String,Object> m,String k,int min,int max) {
    try { int n=Integer.parseInt(Objects.toString(m.get(k),"")); if(n>=min && n<=max) return n; }
    catch(NumberFormatException ignored) {}
    throw new IllegalArgumentException("Enter a valid "+k.replace('_',' '));
  }
  static void validateNetworkAddress(String value) {
    try {
      URI uri=URI.create(value);
      if(!Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null
          || uri.getQuery()!=null || uri.getFragment()!=null || !(uri.getPath().isEmpty() || uri.getPath().equals("/")))
        throw new IllegalArgumentException();
    } catch(Exception e) { throw new IllegalArgumentException("Enter the printer origin, for example https://192.168.1.50"); }
  }
  static String hash(String value) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
    catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }
  private String write(Object value) {
    try { return json.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException("Could not prepare print job",e); }
  }
  private Object parse(String value) {
    try { return json.readValue(value,Map.class); } catch(Exception e) { throw new IllegalStateException("Invalid stored print job",e); }
  }
}
