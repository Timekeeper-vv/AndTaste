package com.example.shixun.service;

/**
 * Immutable result of compiling a creative brief for an image provider.
 * The policy version is persisted with jobs so generated assets remain
 * explainable after prompt rules evolve.
 */
public record GenerationCommand(
        CreativeBrief brief,
        String compiledPrompt,
        String negativePrompt,
        String policyVersion) {

    public GenerationCommand {
        brief = brief == null ? CreativeBrief.empty() : brief;
        compiledPrompt = compiledPrompt == null ? "" : compiledPrompt.trim();
        negativePrompt = negativePrompt == null ? "" : negativePrompt.trim();
        policyVersion = policyVersion == null || policyVersion.isBlank()
                ? ProductPromptPolicy.VERSION : policyVersion.trim();
    }

    /** Compatibility alias for provider adapters that expect a prompt field. */
    public String prompt() {
        return compiledPrompt;
    }

    public String productKey() {
        return brief.productKey();
    }

    public String category() {
        return brief.category();
    }

    public String material() {
        return brief.material();
    }

    public String productSize() {
        return brief.productSize();
    }

    public String rawPrompt() {
        return brief.rawPrompt();
    }

    public Long referenceAssetId() {
        return brief.referenceAssetId();
    }

    /** Alias used by the legacy controller request DTO. */
    public Long inputAssetId() {
        return referenceAssetId();
    }

    public boolean refinement() {
        return brief.refinement();
    }
}
