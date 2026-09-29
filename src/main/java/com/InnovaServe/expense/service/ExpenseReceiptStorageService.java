package com.InnovaServe.expense.service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ExpenseReceiptStorageService {
  private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
  private static final Map<String, String> EXTENSIONS =
      Map.of(
          "application/pdf", ".pdf",
          "image/jpeg", ".jpg",
          "image/png", ".png",
          "image/webp", ".webp");

  private final Path root;

  public ExpenseReceiptStorageService(
      @Value("${app.expense.receipt-storage-dir:./var/expense-receipts}") String directory) {
    root = Paths.get(directory).toAbsolutePath().normalize();
  }

  public String store(UUID tenantId, MultipartFile upload) {
    if (upload == null || upload.isEmpty())
      throw new IllegalArgumentException("Receipt file is required");
    if (upload.getSize() > MAX_FILE_SIZE)
      throw new IllegalArgumentException("Receipt file must be 10 MB or smaller");
    String mediaType = upload.getContentType();
    String extension =
        mediaType == null ? null : EXTENSIONS.get(mediaType.toLowerCase(Locale.ROOT));
    if (extension == null)
      throw new IllegalArgumentException("Receipt must be a PDF, JPEG, PNG, or WebP file");
    try {
      byte[] bytes = upload.getBytes();
      if (!hasValidSignature(mediaType.toLowerCase(Locale.ROOT), bytes))
        throw new IllegalArgumentException("Uploaded file content does not match its media type");
      Path tenantDirectory = tenantDirectory(tenantId);
      Files.createDirectories(tenantDirectory);
      UUID fileId = UUID.randomUUID();
      Path destination = tenantDirectory.resolve(fileId + extension).normalize();
      if (!destination.startsWith(tenantDirectory))
        throw new IllegalArgumentException("Invalid receipt path");
      Files.write(destination, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
      return fileId.toString();
    } catch (IOException ex) {
      throw new IllegalStateException("Could not store receipt file", ex);
    }
  }

  public void requireReceipt(UUID tenantId, String reference) {
    if (reference == null || reference.isBlank()) return;
    try {
      Path fileIdPath = Paths.get(reference);
      if (fileIdPath.getNameCount() != 1) throw new IllegalArgumentException();
      UUID fileId = UUID.fromString(reference);
      if (locate(tenantId, fileId).isEmpty())
        throw new NoSuchElementException("Receipt file not found for this tenant");
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("receipt_file_ref must be a receipt ID returned by upload");
    }
  }

  public ReceiptFile load(UUID tenantId, UUID fileId) {
    try {
      Path path =
          locate(tenantId, fileId)
              .orElseThrow(() -> new NoSuchElementException("Receipt file not found"));
      String extension = path.getFileName().toString().substring(fileId.toString().length());
      String mediaType =
          EXTENSIONS.entrySet().stream()
              .filter(entry -> entry.getValue().equals(extension))
              .map(Map.Entry::getKey)
              .findFirst()
              .orElse("application/octet-stream");
      return new ReceiptFile(Files.readAllBytes(path), mediaType, fileId + extension);
    } catch (IOException ex) {
      throw new IllegalStateException("Could not read receipt file", ex);
    }
  }

  private Optional<Path> locate(UUID tenantId, UUID fileId) {
    Path directory = tenantDirectory(tenantId);
    return EXTENSIONS.values().stream()
        .map(extension -> directory.resolve(fileId + extension).normalize())
        .filter(path -> path.startsWith(directory) && Files.isRegularFile(path))
        .findFirst();
  }

  private Path tenantDirectory(UUID tenantId) {
    Path directory = root.resolve(tenantId.toString()).normalize();
    if (!directory.startsWith(root)) throw new IllegalArgumentException("Invalid tenant file path");
    return directory;
  }

  private boolean hasValidSignature(String mediaType, byte[] bytes) {
    return switch (mediaType) {
      case "application/pdf" -> startsWith(bytes, new byte[] {'%', 'P', 'D', 'F', '-'});
      case "image/jpeg" ->
          bytes.length >= 3
              && (bytes[0] & 0xff) == 0xff
              && (bytes[1] & 0xff) == 0xd8
              && (bytes[2] & 0xff) == 0xff;
      case "image/png" ->
          startsWith(bytes, new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
      case "image/webp" ->
          bytes.length >= 12
              && startsWith(bytes, new byte[] {'R', 'I', 'F', 'F'})
              && bytes[8] == 'W'
              && bytes[9] == 'E'
              && bytes[10] == 'B'
              && bytes[11] == 'P';
      default -> false;
    };
  }

  private boolean startsWith(byte[] bytes, byte[] prefix) {
    if (bytes.length < prefix.length) return false;
    for (int index = 0; index < prefix.length; index++) {
      if (bytes[index] != prefix[index]) return false;
    }
    return true;
  }

  public record ReceiptFile(byte[] content, String mediaType, String filename) {}
}
