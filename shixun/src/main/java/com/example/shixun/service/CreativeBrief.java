package com.example.shixun.service;

/**
 * Provider-neutral creative brief assembled at an API boundary.
 *
 * <p>The brief deliberately contains the physical product choices separately
 * from the free-form request. This prevents a recommended material or size
 * from being lost when a request moves from chat, the H5 workbench, or the
 * durable image queue.</p>
 */
public record CreativeBrief(
        String productKey,
        String category,
        String material,
        String productSize,
        String rawPrompt,
        Long referenceAssetId,
        boolean refinement) {

    public CreativeBrief {
        productKey = clean(productKey);
        category = clean(category);
        material = clean(material);
        productSize = cleanSize(productSize);
        rawPrompt = cleanPrompt(rawPrompt);
        referenceAssetId = positiveId(referenceAssetId);
    }

    public static CreativeBrief empty() {
        return new CreativeBrief("", "", "", "", "", null, false);
    }

    /** Alias used by adapters that still call this field productCategory. */
    public String productCategory() {
        return category;
    }

    public boolean isRefinement() {
        return refinement;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private static String cleanSize(String value) {
        String normalized = clean(value);
        return normalized.length() > 120 ? normalized.substring(0, 120) : normalized;
    }

    private static String cleanPrompt(String value) {
        if (value == null) return "";
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n')
                .lines()
                .map(line -> line.trim().replaceAll("[ \\t]+", " "))
                .collect(java.util.stream.Collectors.joining("\n"))
                .trim();
        return boundPreservingEnds(normalized, 6000);
    }

    private static String boundPreservingEnds(String value, int maxLength) {
        if (value.length() <= maxLength) return value;
        String separator = "\n...[中间描述已压缩]...\n";
        int available = maxLength - separator.length();
        int headLength = (int) Math.ceil(available * 0.58d);
        int tailLength = available - headLength;
        return value.substring(0, headLength) + separator + value.substring(value.length() - tailLength);
    }

    private static Long positiveId(Long value) {
        return value != null && value > 0 ? value : null;
    }
}
