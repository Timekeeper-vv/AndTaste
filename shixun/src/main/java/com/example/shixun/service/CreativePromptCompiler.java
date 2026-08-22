package com.example.shixun.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Stateless application service for turning a structured creative brief into
 * the deterministic prompt contract shared by image providers.
 */
@Service
public final class CreativePromptCompiler {
    public static final String POLICY_VERSION = ProductPromptPolicy.VERSION;
    private static final String DEFAULT_CATEGORY = "文创产品";
    private static final String BRIEF_MARKER = "<<CREATIVE_BRIEF_CONTEXT>>";
    private static final String SIZE_MARKER = "<<PRODUCT_SIZE_LOCK>>";
    private static final String REFERENCE_MARKER = "<<REFERENCE_IMAGE_CONTRACT>>";

    public String policyVersion() {
        return POLICY_VERSION;
    }

    public GenerationCommand compile(CreativeBrief input) {
        return compileInternal(input, null, null);
    }

    /**
     * Compile an optimizer/client candidate without replacing the original
     * wording retained in {@link CreativeBrief#rawPrompt()}.
     */
    public GenerationCommand compileWithCandidate(CreativeBrief input, String promptCandidate) {
        return compileInternal(input, promptCandidate, null);
    }

    private GenerationCommand compileInternal(CreativeBrief input, String promptCandidate,
                                              String requestedNegativePrompt) {
        CreativeBrief brief = normalize(input);
        String policyContext = policyContext(brief);
        String sourcePrompt = stripInternalContracts(mergeCandidateAndOriginal(promptCandidate, brief.rawPrompt()));
        // The 2.5D ice-cream route has a fixed provider template. Only the
        // user's original subject belongs in its subject slot; feeding the
        // already-expanded client prompt back into that slot creates nested
        // templates and weakens the physical product lock.
        if ("ice_cream".equals(ProductPromptPolicy.resolve(brief.category(), brief.material()).key())
                && !brief.rawPrompt().isBlank()) {
            sourcePrompt = brief.rawPrompt();
        }
        String prompt = ProductPromptPolicy.enforce(sourcePrompt, policyContext, brief.material());
        prompt = appendBriefContext(prompt, brief);
        prompt = appendSizeConstraint(prompt, brief.productSize());
        prompt = appendReferenceContract(prompt, brief);
        String negative = ProductPromptPolicy.negative(policyContext, brief.material());
        String requested = requestedNegativePrompt == null ? "" : requestedNegativePrompt.trim();
        if (!requested.isBlank()) {
            negative = negative.isBlank() ? requested : negative + ", " + requested;
        }
        return new GenerationCommand(brief, prompt, negative, policyVersion());
    }

    public GenerationCommand compile(String productKey, String category, String material,
                                     String productSize, String rawPrompt,
                                     Long referenceAssetId, boolean refinement) {
        return compile(new CreativeBrief(productKey, category, material, productSize,
                rawPrompt, referenceAssetId, refinement));
    }

    /**
     * Compile while retaining a caller-supplied negative prompt. The basic
     * overload intentionally returns only policy negatives so adapters can
     * merge provider/style-specific terms at their own boundary.
     */
    public GenerationCommand compile(CreativeBrief input, String requestedNegativePrompt) {
        return compileInternal(input, null, requestedNegativePrompt);
    }

    private String mergeCandidateAndOriginal(String candidate, String original) {
        String primary = cleanPrompt(candidate);
        String raw = cleanPrompt(original);
        if (blank(primary)) return raw;
        if (blank(raw) || primary.equals(raw)) return primary;
        String suffix = "\n用户原始要求（必须保留）：" + raw;
        int budget = 6000;
        if (primary.length() + suffix.length() <= budget) return primary + suffix;
        if (suffix.length() >= budget) {
            String prefix = "用户原始要求（必须保留）：";
            return boundPreservingEnds(prefix + raw, budget);
        }
        int remaining = budget - suffix.length();
        return primary.substring(0, Math.min(primary.length(), remaining)) + suffix;
    }

    private CreativeBrief normalize(CreativeBrief input) {
        CreativeBrief source = input == null ? CreativeBrief.empty() : input;
        String category = blank(source.category()) ? DEFAULT_CATEGORY : source.category();
        return new CreativeBrief(source.productKey(), category, source.material(),
                source.productSize(), source.rawPrompt(), source.referenceAssetId(), source.refinement());
    }

    private String policyContext(CreativeBrief brief) {
        if (blank(brief.productKey())) return brief.category();
        return brief.category() + " " + brief.productKey();
    }

    private String appendBriefContext(String prompt, CreativeBrief brief) {
        List<String> fields = new ArrayList<>();
        if (!blank(brief.productKey())) fields.add("产品选项标识：" + safe(brief.productKey()));
        if (!blank(brief.category())) fields.add("产品类别：" + safe(brief.category()));
        if (!blank(brief.material())) fields.add("制造材质：" + safe(brief.material()));
        if (fields.isEmpty()) return prompt;
        return prompt + "\n" + BRIEF_MARKER + String.join("；", fields)
                + "。以上已确认的产品字段必须原样保留，不得改成无关产品或材质；字段标识仅供生成约束使用，不要把标识文字渲染到成品上。<</CREATIVE_BRIEF_CONTEXT>>";
    }

    private String appendSizeConstraint(String prompt, String productSize) {
        if (blank(productSize)) return prompt;
        return prompt + "\n" + SIZE_MARKER + "成品物理尺寸为 " + safe(productSize)
                + "。必须保持与该规格匹配的真实比例、厚度和可生产结构；这是成品尺寸，不是图片分辨率。<</PRODUCT_SIZE_LOCK>>";
    }

    private String appendReferenceContract(String prompt, CreativeBrief brief) {
        if (brief.referenceAssetId() == null) return prompt;
        String instruction = brief.refinement()
                ? "这是图生图修改任务：保留参考图主体身份、可识别轮廓和主要文化元素，同时必须明显执行用户要求的修改，不得原封不动返回参考图。"
                : "这是图生图文创转化任务：参考图是主体视觉来源，保留其可识别元素并明确转化为已选产品，不得替换成无关主体或只输出原图。";
        return prompt + "\n" + REFERENCE_MARKER + instruction
                + "参考资产已由服务端随请求提供，禁止忽略参考图。<</REFERENCE_IMAGE_CONTRACT>>";
    }

    private static String safe(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n]+", " ").trim();
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

    private String stripInternalContracts(String value) {
        if (blank(value)) return "";
        return value
                .replaceAll("(?s)<<CREATIVE_BRIEF_CONTEXT>>.*?<</CREATIVE_BRIEF_CONTEXT>>", "")
                .replaceAll("(?s)<<PRODUCT_SIZE_LOCK>>.*?<</PRODUCT_SIZE_LOCK>>", "")
                .replaceAll("(?s)<<REFERENCE_IMAGE_CONTRACT>>.*?<</REFERENCE_IMAGE_CONTRACT>>", "")
                .trim();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
