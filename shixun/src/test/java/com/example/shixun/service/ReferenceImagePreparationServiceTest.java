package com.example.shixun.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceImagePreparationServiceTest {

    private final ReferenceImagePreparationService service = new ReferenceImagePreparationService();

    @Test
    void unreadableUploadReturnsAnActionableQualityWarning(@TempDir Path temp) throws Exception {
        Path source = temp.resolve("not-an-image.png");
        Files.writeString(source, "not an image", StandardCharsets.UTF_8);

        ReferenceImagePreparationService.Preparation preparation = service.prepare(
                source, temp.resolve("uploads"), "ref-1");

        assertThat(preparation.readable()).isFalse();
        assertThat(preparation.generationPath()).isNull();
        assertThat(preparation.warnings()).containsExactly("image_metadata_unavailable");
        assertThat(preparation.quality()).containsEntry("readable", false);
    }

    @Test
    void oversizedDimensionsCreateAProviderBoundedDerivativeAndSanitizeTheStem(@TempDir Path temp)
            throws Exception {
        Path source = temp.resolve("source.png");
        BufferedImage image = new BufferedImage(4000, 2000, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, Color.RED.getRGB());
        ImageIO.write(image, "png", source.toFile());
        Path uploadDirectory = temp.resolve("uploads");

        ReferenceImagePreparationService.Preparation preparation = service.prepare(
                source, uploadDirectory, "../unsafe stem");

        assertThat(preparation.readable()).isTrue();
        assertThat(preparation.quality()).containsEntry("normalizedForGeneration", true);
        assertThat(preparation.generationPath()).isNotEqualTo(source.toAbsolutePath().normalize());
        assertThat(preparation.generationPath().getParent()).isEqualTo(uploadDirectory.toAbsolutePath().normalize());
        assertThat(preparation.generationPath().getFileName().toString()).isEqualTo("___unsafe_stem-generation.jpg");
        assertThat(preparation.quality().get("generationWidth")).isEqualTo(3072);
        assertThat(preparation.quality().get("generationHeight")).isEqualTo(1536);
        assertThat(Files.size(preparation.generationPath()))
                .isLessThanOrEqualTo(ReferenceImagePreparationService.MAX_GENERATION_BYTES);
    }

    @Test
    void oversizedEncodedPayloadIsRecompressedEvenWhenDimensionsAreAlreadyWithinBounds(@TempDir Path temp)
            throws Exception {
        Path source = temp.resolve("large-payload.png");
        BufferedImage image = new BufferedImage(3072, 3072, BufferedImage.TYPE_INT_RGB);
        int value = 0x13579bdf;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                value = value * 1103515245 + 12345;
                image.setRGB(x, y, value);
            }
        }
        ImageIO.write(image, "png", source.toFile());
        assertThat(Files.size(source)).isGreaterThan(ReferenceImagePreparationService.MAX_GENERATION_BYTES);

        ReferenceImagePreparationService.Preparation preparation = service.prepare(
                source, temp.resolve("uploads"), "ref-large");

        assertThat(preparation.readable()).isTrue();
        assertThat(preparation.quality()).containsEntry("normalizedForGeneration", true);
        assertThat(preparation.generationPath().getFileName().toString()).isEqualTo("ref-large-generation.jpg");
        assertThat(preparation.quality().get("generationWidth")).isEqualTo(3072);
        assertThat(Files.size(preparation.generationPath()))
                .isLessThanOrEqualTo(ReferenceImagePreparationService.MAX_GENERATION_BYTES);
    }

    @Test
    void smallImageUsesTheOriginalPixelsWithoutCreatingADerivative(@TempDir Path temp) throws Exception {
        Path source = temp.resolve("source.jpg");
        BufferedImage image = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, Color.BLUE.getRGB());
        ImageIO.write(image, "jpg", source.toFile());

        ReferenceImagePreparationService.Preparation preparation = service.prepare(
                source, temp.resolve("uploads"), "ref-2");

        assertThat(preparation.readable()).isTrue();
        assertThat(preparation.generationPath()).isEqualTo(source.toAbsolutePath().normalize());
        assertThat(preparation.quality()).containsEntry("normalizedForGeneration", false);
        assertThat(preparation.quality()).containsEntry("generationWidth", 640);
        assertThat(preparation.quality()).containsEntry("generationHeight", 480);
        assertThat(preparation.quality()).containsEntry("generationBytes", Files.size(source));
    }
}
