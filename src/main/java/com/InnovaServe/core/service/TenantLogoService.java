package com.InnovaServe.core.service;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Iterator;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TenantLogoService {
  public byte[] normalize(MultipartFile upload) {
    if (upload == null || upload.isEmpty() || upload.getSize() > 2 * 1024 * 1024)
      throw new IllegalArgumentException("Choose a PNG or JPEG logo up to 2 MB");
    try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(upload.getBytes()))) {
      Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw new IllegalArgumentException("Choose a valid PNG or JPEG logo");
      ImageReader reader = readers.next();
      try {
        String format = reader.getFormatName();
        if (!format.equalsIgnoreCase("png") && !format.equalsIgnoreCase("jpeg"))
          throw new IllegalArgumentException("Choose a PNG or JPEG logo");
        reader.setInput(input);
        int width = reader.getWidth(0), height = reader.getHeight(0);
        if (width < 1 || height < 1 || (long) width * height > 16000000)
          throw new IllegalArgumentException("Logo must contain at most 16 million pixels");
        BufferedImage original = reader.read(0);
        double scale = Math.min(1, 512.0 / Math.max(width, height));
        BufferedImage resized = new BufferedImage(Math.max(1, (int) (width * scale)),
            Math.max(1, (int) (height * scale)), BufferedImage.TYPE_INT_ARGB);
        var graphics = resized.createGraphics();
        try {
          graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
          graphics.drawImage(original, 0, 0, resized.getWidth(), resized.getHeight(), null);
        } finally { graphics.dispose(); }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(resized, "png", output);
        return output.toByteArray();
      } finally { reader.dispose(); }
    } catch (IOException ex) {
      throw new IllegalArgumentException("Could not read the logo. Choose a valid PNG or JPEG image", ex);
    }
  }
}
