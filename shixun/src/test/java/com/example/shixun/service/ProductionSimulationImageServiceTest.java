package com.example.shixun.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionSimulationImageServiceTest {
    private final ProductionSimulationImageService service = new ProductionSimulationImageService();

    @Test
    void createsEqualWidthFrontSideBackJpegsInOrder(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("simulation.png");
        BufferedImage image = new BufferedImage(10, 4, BufferedImage.TYPE_INT_RGB);
        fill(image, 0, 3, Color.RED);
        fill(image, 3, 3, Color.GREEN);
        fill(image, 6, 3, Color.BLUE);
        ImageIO.write(image, "png", source.toFile());

        ProductionSimulationImageService.Triptych result = service.split(source, tempDir.resolve("generated"), "test-sheet");

        assertEquals(10, result.width());
        assertEquals(4, result.height());
        assertEquals(List.of("front", "left", "back"), List.copyOf(result.paths().keySet()));
        int expectedPanelWidth = 3;
        int[] expectedColors = {Color.RED.getRGB(), Color.GREEN.getRGB(), Color.BLUE.getRGB()};
        int index = 0;
        for (String view : List.of("front", "left", "back")) {
            Path path = Path.of(result.paths().get(view));
            assertTrue(path.getFileName().toString().endsWith(".jpg"));
            BufferedImage crop = ImageIO.read(path.toFile());
            assertEquals(expectedPanelWidth, crop.getWidth());
            assertEquals(4, crop.getHeight());
            assertColorClose(expectedColors[index++], crop.getRGB(1, 1));
        }
    }

    @Test
    void rejectsMissingOrUnreadableImages(@TempDir Path tempDir) throws Exception {
        assertThrows(IOException.class, () -> service.split(tempDir.resolve("missing.png"), tempDir, "missing"));

        Path invalid = tempDir.resolve("invalid.png");
        Files.writeString(invalid, "not an image");
        IOException error = assertThrows(IOException.class, () -> service.split(invalid, tempDir, "invalid"));
        assertTrue(error.getMessage().contains("无法解码"));
    }

    private static void fill(BufferedImage image, int x, int width, Color color) {
        for (int px = x; px < x + width; px++) {
            for (int py = 0; py < image.getHeight(); py++) image.setRGB(px, py, color.getRGB());
        }
    }

    private static void assertColorClose(int expected, int actual) {
        Color expectedColor = new Color(expected);
        Color actualColor = new Color(actual);
        assertTrue(Math.abs(expectedColor.getRed() - actualColor.getRed()) < 35);
        assertTrue(Math.abs(expectedColor.getGreen() - actualColor.getGreen()) < 35);
        assertTrue(Math.abs(expectedColor.getBlue() - actualColor.getBlue()) < 35);
    }
}
