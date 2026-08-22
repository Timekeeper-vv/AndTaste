package com.example.shixun.service;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Keeps the user's original upload intact while preparing a predictable
 * provider input. Mobile image uploads are often either very large or already
 * aggressively compressed; a bounded, high-quality derivative gives the
 * image model a stable input without destroying the source asset.
 */
@Service
public class ReferenceImagePreparationService {
    public static final int MAX_GENERATION_DIMENSION = 3072;
    /** Seedream accepts an image payload up to 15 MiB. Keep the derivative below that limit. */
    public static final long MAX_GENERATION_BYTES = 15L * 1024L * 1024L;
    private static final int MIN_RECOMMENDED_DIMENSION = 512;
    private static final int MIN_GENERATION_DIMENSION = 256;

    public Preparation prepare(Path original, Path uploadDirectory, String fileStem) throws IOException {
        if (original == null) return Preparation.unreadable();
        Path sourcePath = original.toAbsolutePath().normalize();
        if (!Files.isRegularFile(sourcePath)) return Preparation.unreadable();
        BufferedImage source = ImageIO.read(sourcePath.toFile());
        if (source == null) {
            return Preparation.unreadable();
        }

        int width = source.getWidth();
        int height = source.getHeight();
        boolean alpha = source.getColorModel().hasAlpha();
        double megapixels = (width * (double) height) / 1_000_000d;
        List<String> warnings = new ArrayList<>();
        if (Math.min(width, height) < MIN_RECOMMENDED_DIMENSION) {
            warnings.add("reference_resolution_low");
        }
        if (width < height / 3 || height < width / 3) {
            warnings.add("reference_aspect_extreme");
        }

        int maxDimension = Math.max(width, height);
        long sourceBytes = Files.size(sourcePath);
        boolean normalized = maxDimension > MAX_GENERATION_DIMENSION || sourceBytes > MAX_GENERATION_BYTES;
        int generationWidth = width;
        int generationHeight = height;
        Path generationPath = sourcePath;
        String generationExtension = extensionFor(sourcePath, alpha);
        if (normalized) {
            double scale = MAX_GENERATION_DIMENSION / (double) maxDimension;
            if (maxDimension <= MAX_GENERATION_DIMENSION) scale = 1d;
            int targetWidth = Math.max(1, (int) Math.round(width * scale));
            int targetHeight = Math.max(1, (int) Math.round(height * scale));
            generationExtension = alpha ? ".png" : ".jpg";
            Path generationRoot = uploadDirectory == null
                    ? sourcePath.getParent()
                    : uploadDirectory.toAbsolutePath().normalize();
            if (generationRoot == null) throw new IOException("参考图目录无效");
            Files.createDirectories(generationRoot);
            String safeStem = safeFileStem(fileStem);
            generationPath = generationRoot.resolve(safeStem + "-generation" + generationExtension).normalize();
            if (!generationPath.startsWith(generationRoot)) throw new IOException("参考图标准化路径无效");

            // A high-resolution PNG can still exceed the provider limit after the
            // first resize. Reduce dimensions in bounded steps until the encoded
            // derivative is accepted, while keeping enough detail for inference.
            while (true) {
                BufferedImage resized = resize(source, targetWidth, targetHeight, alpha);
                writeImage(resized, alpha ? "png" : "jpg", generationPath);
                long generationBytes = Files.size(generationPath);
                if (generationBytes <= MAX_GENERATION_BYTES
                        || Math.max(targetWidth, targetHeight) <= MIN_GENERATION_DIMENSION) {
                    generationWidth = targetWidth;
                    generationHeight = targetHeight;
                    if (generationBytes > MAX_GENERATION_BYTES) {
                        Files.deleteIfExists(generationPath);
                        throw new IOException("参考图标准化后仍超过 15MB，请压缩后重试");
                    }
                    break;
                }
                double compressionScale = Math.sqrt(MAX_GENERATION_BYTES / (double) generationBytes) * 0.94d;
                int nextWidth = Math.max(1, (int) Math.floor(targetWidth * compressionScale));
                int nextHeight = Math.max(1, (int) Math.floor(targetHeight * compressionScale));
                if (nextWidth >= targetWidth && nextHeight >= targetHeight) {
                    nextWidth = Math.max(1, (int) Math.floor(targetWidth * 0.85d));
                    nextHeight = Math.max(1, (int) Math.floor(targetHeight * 0.85d));
                }
                targetWidth = nextWidth;
                targetHeight = nextHeight;
            }
        }

        Map<String, Object> quality = new LinkedHashMap<>();
        quality.put("sourceWidth", width);
        quality.put("sourceHeight", height);
        quality.put("sourceMegapixels", Math.round(megapixels * 100.0d) / 100.0d);
        quality.put("sourceAspectRatio", Math.round((width / (double) height) * 1000.0d) / 1000.0d);
        quality.put("hasAlpha", alpha);
        quality.put("normalizedForGeneration", normalized);
        quality.put("readable", true);
        quality.put("generationWidth", generationWidth);
        quality.put("generationHeight", generationHeight);
        quality.put("generationBytes", normalized ? Files.size(generationPath) : sourceBytes);
        quality.put("warnings", warnings);
        quality.put("analysisVersion", "reference-quality-v1");
        return new Preparation(generationPath, generationExtension, quality, warnings, true);
    }

    private void writeImage(BufferedImage image, String format, Path output) throws IOException {
        if (!ImageIO.write(image, format, output.toFile())) {
            throw new IOException("无法写入参考图标准化副本");
        }
    }

    private String safeFileStem(String fileStem) {
        String value = fileStem == null ? "reference" : fileStem.trim();
        if (value.isEmpty()) value = "reference";
        return value.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private BufferedImage resize(BufferedImage source, int width, int height, boolean alpha) {
        int type = alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage target = new BufferedImage(width, height, type);
        Graphics2D graphics = target.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return target;
    }

    private String extensionFor(Path path, boolean alpha) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (alpha || name.endsWith(".png")) return ".png";
        return ".jpg";
    }

    public record Preparation(Path generationPath, String generationExtension,
                              Map<String, Object> quality, List<String> warnings,
                              boolean readable) {
        static Preparation unreadable() {
            return new Preparation(null, "", Map.of(
                    "analysisVersion", "reference-quality-v1",
                    "readable", false,
                    "warnings", List.of("image_metadata_unavailable")
            ), List.of("image_metadata_unavailable"), false);
        }
    }
}
