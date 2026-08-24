package com.example.shixun.service;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;

/**
 * Validates a generated horizontal production-simulation image and creates
 * deterministic front/side/back crops for the existing 3D workflow.
 */
@Service
public class ProductionSimulationImageService {
    public Triptych split(Path source, Path outputDirectory, String fileStem) throws IOException {
        if (source == null || !Files.isRegularFile(source)) {
            throw new IOException("生产模拟图文件不存在");
        }
        BufferedImage image = ImageIO.read(source.toFile());
        if (image == null) throw new IOException("生产模拟图无法解码，请重新生成");
        int width = image.getWidth();
        int height = image.getHeight();
        if (width < 3 || height < 1) throw new IOException("生产模拟图尺寸无效，无法切分三个视角");

        Files.createDirectories(outputDirectory);
        String stem = safeStem(fileStem);
        int panelWidth = width / 3;
        int croppedWidth = panelWidth * 3;
        if (panelWidth <= 0 || croppedWidth < 3) {
            throw new IOException("生产模拟图宽度不足，无法切分三个等宽视角");
        }
        // Generated sheets occasionally contain a one- or two-pixel remainder.
        // Drop it symmetrically so every production slice has exactly the same
        // dimensions and the panel boundaries remain deterministic.
        int horizontalOffset = (width - croppedWidth) / 2;

        Map<String, String> paths = new LinkedHashMap<>();
        String[] views = {"front", "left", "back"};
        for (int i = 0; i < views.length; i++) {
            int start = horizontalOffset + i * panelWidth;
            BufferedImage crop = copyCrop(image, start, panelWidth, height);
            String fileName = stem + "-" + views[i] + ".jpg";
            Path outputRoot = outputDirectory.toAbsolutePath().normalize();
            Path target = outputRoot.resolve(fileName).normalize();
            if (!target.startsWith(outputRoot)) {
                throw new IOException("生产模拟图切片路径无效");
            }
            if (!ImageIO.write(crop, "jpg", target.toFile())) {
                throw new IOException("无法保存生产模拟图的" + views[i] + "切片");
            }
            paths.put(views[i], target.toString());
        }
        return new Triptych(width, height, paths);
    }

    private BufferedImage copyCrop(BufferedImage source, int x, int width, int height) {
        BufferedImage crop = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = crop.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source, 0, 0, width, height, x, 0, x + width, height, null);
        graphics.dispose();
        return crop;
    }

    private String safeStem(String value) {
        String stem = value == null ? "production-simulation" : value.trim();
        if (stem.isEmpty()) stem = "production-simulation";
        return stem.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    public record Triptych(int width, int height, Map<String, String> paths) {
        public Triptych {
            paths = Collections.unmodifiableMap(new LinkedHashMap<>(paths));
        }
    }
}
