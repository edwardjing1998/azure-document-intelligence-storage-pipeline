package com.example.documentintelligence.service;

import com.example.documentintelligence.config.ImageCleaningProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Optional lightweight cleaner. It removes strongly colored marks by replacing
 * them with nearby pixels. It is intentionally disabled by default and is not
 * a general handwriting-segmentation or ML inpainting solution.
 */
@Service
public class ImageCleaningService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImageCleaningService.class);
    private final ImageCleaningProperties properties;

    public ImageCleaningService(ImageCleaningProperties properties) {
        this.properties = properties;
    }

    public byte[] prepareForAnalysis(byte[] original, String contentType) throws IOException {
        if (!properties.enabled()) {
            return original;
        }
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(original));
            if (source == null) {
                throw new IOException("The image format could not be decoded.");
            }
            BufferedImage cleaned = cleanColoredMarks(source);
            String format = contentType != null && contentType.toLowerCase().contains("png")
                    ? "png" : "jpg";
            ByteArrayOutputStream output = new ByteArrayOutputStream(original.length);
            if (!ImageIO.write(cleaned, format, output)) {
                throw new IOException("No ImageIO writer available for " + format);
            }
            return output.toByteArray();
        } catch (Exception ex) {
            if (properties.failOnError()) {
                throw ex instanceof IOException io ? io : new IOException(ex);
            }
            LOGGER.warn("Image cleaning failed; using the original image: {}", ex.getMessage());
            return original;
        }
    }

    private BufferedImage cleanColoredMarks(BufferedImage source) {
        BufferedImage result = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                Color color = new Color(source.getRGB(x, y), true);
                float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
                int brightness = Math.round(hsb[2] * 255);
                int saturation = Math.round(hsb[1] * 255);
                if (saturation >= properties.saturationThreshold()
                        && brightness >= properties.minimumBrightness()) {
                    result.setRGB(x, y, neighborAverage(source, x, y));
                } else {
                    result.setRGB(x, y, new Color(color.getRed(), color.getGreen(), color.getBlue()).getRGB());
                }
            }
        }
        return result;
    }

    private int neighborAverage(BufferedImage image, int x, int y) {
        long red = 0, green = 0, blue = 0, count = 0;
        int radius = properties.radius();
        for (int yy = Math.max(0, y - radius); yy <= Math.min(image.getHeight() - 1, y + radius); yy++) {
            for (int xx = Math.max(0, x - radius); xx <= Math.min(image.getWidth() - 1, x + radius); xx++) {
                if (xx == x && yy == y) continue;
                Color c = new Color(image.getRGB(xx, yy), true);
                red += c.getRed(); green += c.getGreen(); blue += c.getBlue(); count++;
            }
        }
        return new Color((int) (red / count), (int) (green / count), (int) (blue / count)).getRGB();
    }
}
