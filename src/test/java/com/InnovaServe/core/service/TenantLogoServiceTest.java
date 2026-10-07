package com.InnovaServe.core.service;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class TenantLogoServiceTest {
  private final TenantLogoService service = new TenantLogoService();

  @Test
  void convertsAndResizesLogoToPngPreservingAspectRatio() throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(1024, 512, BufferedImage.TYPE_INT_RGB), "jpeg", bytes);
    byte[] result = service.normalize(new MockMultipartFile("file", "logo.jpg", "image/jpeg", bytes.toByteArray()));
    BufferedImage image = ImageIO.read(new ByteArrayInputStream(result));
    assertEquals(512, image.getWidth());
    assertEquals(256, image.getHeight());
    assertEquals((byte) 0x89, result[0]);
  }

  @Test
  void rejectsSpoofedImageAndOversizedUpload() {
    assertThrows(IllegalArgumentException.class, () -> service.normalize(
        new MockMultipartFile("file", "logo.png", "image/png", "<svg/>".getBytes())));
    assertThrows(IllegalArgumentException.class, () -> service.normalize(
        new MockMultipartFile("file", "large.png", "image/png", new byte[2 * 1024 * 1024 + 1])));
  }
}
