package io.github.opencraftai.minecraftchat.service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum AiProviderType {
    OPENAI("openai", "OpenAI", ProviderFamily.OPENAI_COMPATIBLE, "https://api.openai.com", "gpt-4.1-mini", true),
    OPENROUTER("openrouter", "OpenRouter", ProviderFamily.OPENAI_COMPATIBLE, "https://openrouter.ai/api", "openai/gpt-4.1-mini", true),
    GROK("grok", "Grok", ProviderFamily.OPENAI_COMPATIBLE, "https://api.x.ai", "grok-3-mini", true),
    TOGETHER("together", "Together", ProviderFamily.OPENAI_COMPATIBLE, "https://api.together.xyz", "meta-llama/Llama-3.3-70B-Instruct-Turbo", true),
    DEEPSEEK("deepseek", "DeepSeek", ProviderFamily.OPENAI_COMPATIBLE, "https://api.deepseek.com", "deepseek-chat", true),
    ANTHROPIC("anthropic", "Anthropic", ProviderFamily.ANTHROPIC, "https://api.anthropic.com", "claude-3-5-haiku-latest", true),
    GEMINI("gemini", "Gemini", ProviderFamily.GEMINI, "https://generativelanguage.googleapis.com", "gemini-2.5-flash", true),
    OLLAMA("ollama", "Ollama", ProviderFamily.OLLAMA, "http://127.0.0.1:11434", "llama3.1:8b", false);

    private final String id;
    private final String displayName;
    private final ProviderFamily family;
    private final String defaultBaseUrl;
    private final String defaultModel;
    private final boolean requiresApiKey;

    AiProviderType(String id, String displayName, ProviderFamily family, String defaultBaseUrl, String defaultModel, boolean requiresApiKey) {
        this.id = id;
        this.displayName = displayName;
        this.family = family;
        this.defaultBaseUrl = defaultBaseUrl;
        this.defaultModel = defaultModel;
        this.requiresApiKey = requiresApiKey;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public ProviderFamily family() {
        return family;
    }

    public String defaultBaseUrl() {
        return defaultBaseUrl;
    }

    public String defaultModel() {
        return defaultModel;
    }

    public boolean requiresApiKey() {
        return requiresApiKey;
    }

    public String environmentVariable() {
        return "MINECRAFT_AI_" + id.toUpperCase(Locale.ROOT) + "_API_KEY";
    }

    public static Optional<AiProviderType> fromId(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String normalized = value.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
            .filter(provider -> provider.id.equals(normalized))
            .findFirst();
    }

    public enum ProviderFamily {
        OPENAI_COMPATIBLE,
        ANTHROPIC,
        GEMINI,
        OLLAMA
    }
}
