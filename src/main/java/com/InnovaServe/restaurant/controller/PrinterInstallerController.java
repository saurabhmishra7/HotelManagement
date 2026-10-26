package com.InnovaServe.restaurant.controller;

import com.InnovaServe.restaurant.service.PrinterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/printer-devices")
public class PrinterInstallerController {
  private final PrinterService printers;
  private final ObjectMapper json;
  public PrinterInstallerController(PrinterService printers,ObjectMapper json) { this.printers=printers; this.json=json; }
  @PostMapping("/{id}/agent-download")
  @PreAuthorize("hasAuthority('PERM_PRINTER_OPERATOR')")
  public ResponseEntity<byte[]> download(@PathVariable UUID id,@RequestBody Map<String,String> input) throws IOException {
    String api=input.getOrDefault("api_url","");
    URI uri;
    try { uri=URI.create(api); } catch(Exception e) { throw new IllegalArgumentException("Enter a valid backend API URL"); }
    if(!Set.of("https","http").contains(Objects.toString(uri.getScheme(),"")) || uri.getHost()==null
        || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null || api.length()>500)
      throw new IllegalArgumentException("Enter a valid backend API URL");
    var output=new ByteArrayOutputStream();
    try(var zip=new ZipOutputStream(output)) {
      for(String name:List.of("Setup.cmd","setup.ps1","agent.ps1","printer.ps1","README.txt")) {
        zip.putNextEntry(new ZipEntry(name));
        try(var in=new ClassPathResource("printer-agent/"+name).getInputStream()) { in.transferTo(zip); }
        zip.closeEntry();
      }
      String key=printers.agentKey(id);
      zip.putNextEntry(new ZipEntry("pairing.json"));
      zip.write(json.writeValueAsString(Map.of("device_id",id,"device_key",key,"api_url",api)).getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"InnovaServe-Printer-Setup.zip\"")
        .contentType(MediaType.APPLICATION_OCTET_STREAM).body(output.toByteArray());
  }
}
