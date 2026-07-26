package io.github.opencraftai.minecraftchat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.opencraftai.minecraftchat.MinecraftAiChatPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class AiService {

    private final MinecraftAiChatPlugin plugin;
    private final SecretsStore secretsStore;
    private final ConversationStore conversationStore;
    private final Executor requestExecutor;
    private final AiSafetyFilter safetyFilter;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger activeRequests = new AtomicInteger();
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();

    public AiService(
        MinecraftAiChatPlugin plugin,
        SecretsStore secretsStore,
        ConversationStore conversationStore,
        Executor requestExecutor
    ) {
        this.plugin = plugin;
        this.secretsStore = secretsStore;
        this.conversationStore = conversationStore;
        this.requestExecutor = requestExecutor;
        this.safetyFilter = new AiSafetyFilter(plugin);
    }

    public void ask(CommandSender sender, String prompt, Player player) {
        sender.sendMessage(colorize(plugin.getConfig().getString("chat.waitMessage", "&7Thinking...")));

        AiProviderType provider = getSelectedProvider();
        if (!tryReserveRequestSlot()) {
            sender.sendMessage(colorize(plugin.getConfig().getString(
                "chat.busyMessage",
                "&cThe AI is busy right now. Please try again in a moment."
            )));
            return;
        }

        CompletableFuture
            .supplyAsync(() -> requestReply(prompt, player, provider), requestExecutor)
            .whenComplete((response, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                releaseRequestSlot();
                if (throwable != null) {
                    plugin.getLogger().warning("AI request failed for provider " + provider.id() + ".");
                    sender.sendMessage(colorize(plugin.getConfig().getString("chat.errorMessage", "&cThe AI request failed.")));
                    return;
                }

                if (player != null) {
                    UUID playerId = player.getUniqueId();
                    conversationStore.addUserMessage(playerId, prompt);
                    conversationStore.addAssistantMessage(playerId, response);
                }

                sendSplitResponse(sender, response);
            }));
    }

    private String requestReply(String prompt, Player player, AiProviderType provider) {
        String apiKey = secretsStore.getApiKey(provider);
        if (provider.requiresApiKey() && apiKey.isBlank()) {
            throw new IllegalStateException("No API key is set for provider " + provider.id());
        }

        String configuredBaseUrl = plugin.getConfig().getString("providers." + provider.id() + ".baseUrl", provider.defaultBaseUrl());
        String baseUrl = validateBaseUrl(provider, configuredBaseUrl);
        String model = plugin.getConfig().getString("model", provider.defaultModel()).trim();
        if (model.isBlank()) {
            model = provider.defaultModel();
        }

        String systemPrompt = plugin.getConfig().getString("systemPrompt",
            "You are a helpful Minecraft server assistant. Keep answers short, simple, and easy to read in Minecraft chat.");
        double temperature = plugin.getConfig().getDouble("temperature", 0.6D);
        int maxTokens = Math.max(64, plugin.getConfig().getInt("maxTokens", 180));

        List<ChatMessage> messages = new ArrayList<>();
        if (player != null) {
            messages.addAll(conversationStore.getHistory(player.getUniqueId()));
        }
        messages.add(new ChatMessage("user", prompt));

        HttpRequest request = buildRequest(provider, baseUrl, model, apiKey, systemPrompt, messages, temperature, maxTokens);

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Provider returned HTTP " + response.statusCode());
            }

            String text = parseResponse(provider, response.body()).trim();
            if (text.isEmpty()) {
                throw new IllegalStateException("Provider returned an empty reply.");
            }

            return safetyFilter.filter(text);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Request to provider failed.", exception);
        }
    }

    private HttpRequest buildRequest(
        AiProviderType provider,
        String baseUrl,
        String model,
        String apiKey,
        String systemPrompt,
        List<ChatMessage> messages,
        double temperature,
        int maxTokens
    ) {
        try {
            return switch (provider.family()) {
                case OPENAI_COMPATIBLE -> buildOpenAiCompatibleRequest(baseUrl, model, apiKey, systemPrompt, messages, temperature, maxTokens);
                case ANTHROPIC -> buildAnthropicRequest(baseUrl, model, apiKey, systemPrompt, messages, temperature, maxTokens);
                case GEMINI -> buildGeminiRequest(baseUrl, model, apiKey, systemPrompt, messages, temperature, maxTokens);
                case OLLAMA -> buildOllamaRequest(baseUrl, model, systemPrompt, messages, temperature, maxTokens);
            };
        } catch (IOException exception) {
            throw new IllegalStateException("Could not serialize provider request.", exception);
        }
    }

    private HttpRequest buildOpenAiCompatibleRequest(
        String baseUrl,
        String model,
        String apiKey,
        String systemPrompt,
        List<ChatMessage> messages,
        double temperature,
        int maxTokens
    ) throws IOException {
        List<Object> payloadMessages = new ArrayList<>();
        payloadMessages.add(java.util.Map.of("role", "system", "content", buildShortPrompt(systemPrompt)));
        for (ChatMessage message : messages) {
            payloadMessages.add(java.util.Map.of("role", message.role(), "content", message.content()));
        }

        String body = objectMapper.writeValueAsString(java.util.Map.of(
            "model", model,
            "messages", payloadMessages,
            "temperature", temperature,
            "max_tokens", maxTokens
        ));

        return HttpRequest.newBuilder()
            .uri(URI.create(joinUrl(baseUrl, "/v1/chat/completions")))
            .timeout(Duration.ofSeconds(45))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
    }

    private HttpRequest buildAnthropicRequest(
        String baseUrl,
        String model,
        String apiKey,
        String systemPrompt,
        List<ChatMessage> messages,
        double temperature,
        int maxTokens
    ) throws IOException {
        List<Object> anthropicMessages = new ArrayList<>();
        for (ChatMessage message : messages) {
            anthropicMessages.add(java.util.Map.of(
                "role", message.role(),
                "content", List.of(java.util.Map.of("type", "text", "text", message.content()))
            ));
        }

        String body = objectMapper.writeValueAsString(java.util.Map.of(
            "model", model,
            "system", buildShortPrompt(systemPrompt),
            "messages", anthropicMessages,
            "temperature", temperature,
            "max_tokens", maxTokens
        ));

        return HttpRequest.newBuilder()
            .uri(URI.create(joinUrl(baseUrl, "/v1/messages")))
            .timeout(Duration.ofSeconds(45))
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
    }

    private HttpRequest buildGeminiRequest(
        String baseUrl,
        String model,
        String apiKey,
        String systemPrompt,
        List<ChatMessage> messages,
        double temperature,
        int maxTokens
    ) throws IOException {
        List<Object> contents = new ArrayList<>();
        for (ChatMessage message : messages) {
            String role = "assistant".equals(message.role()) ? "model" : "user";
            contents.add(java.util.Map.of(
                "role", role,
                "parts", List.of(java.util.Map.of("text", message.content()))
            ));
        }

        String body = objectMapper.writeValueAsString(java.util.Map.of(
            "systemInstruction", java.util.Map.of(
                "parts", List.of(java.util.Map.of("text", buildShortPrompt(systemPrompt)))
            ),
            "contents", contents,
            "generationConfig", java.util.Map.of(
                "temperature", temperature,
                "maxOutputTokens", maxTokens
            )
        ));

        String encodedModel = URLEncoder.encode(model, StandardCharsets.UTF_8);
        String requestUrl = stripTrailingSlash(baseUrl) + "/v1beta/models/" + encodedModel + ":generateContent";

        return HttpRequest.newBuilder()
            .uri(URI.create(requestUrl))
            .timeout(Duration.ofSeconds(45))
            .header("x-goog-api-key", apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
    }

    private HttpRequest buildOllamaRequest(
        String baseUrl,
        String model,
        String systemPrompt,
        List<ChatMessage> messages,
        double temperature,
        int maxTokens
    ) throws IOException {
        List<Object> payloadMessages = new ArrayList<>();
        payloadMessages.add(java.util.Map.of("role", "system", "content", buildShortPrompt(systemPrompt)));
        for (ChatMessage message : messages) {
            payloadMessages.add(java.util.Map.of("role", message.role(), "content", message.content()));
        }

        String body = objectMapper.writeValueAsString(java.util.Map.of(
            "model", model,
            "stream", false,
            "messages", payloadMessages,
            "options", java.util.Map.of(
                "temperature", temperature,
                "num_predict", maxTokens
            )
        ));

        return HttpRequest.newBuilder()
            .uri(URI.create(joinUrl(baseUrl, "/api/chat")))
            .timeout(Duration.ofSeconds(45))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
    }

    private String parseResponse(AiProviderType provider, String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        return switch (provider.family()) {
            case OPENAI_COMPATIBLE -> readOpenAiCompatibleResponse(root);
            case ANTHROPIC -> readAnthropicResponse(root);
            case GEMINI -> readGeminiResponse(root);
            case OLLAMA -> readOllamaResponse(root);
        };
    }

    private String readOpenAiCompatibleResponse(JsonNode root) {
        JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
        return readFlexibleTextNode(contentNode);
    }

    private String readAnthropicResponse(JsonNode root) {
        JsonNode content = root.path("content");
        if (!content.isArray()) {
            return "";
        }

        StringJoiner joiner = new StringJoiner(" ");
        for (JsonNode item : content) {
            if ("text".equals(item.path("type").asText())) {
                String text = item.path("text").asText("").trim();
                if (!text.isEmpty()) {
                    joiner.add(text);
                }
            }
        }
        return joiner.toString();
    }

    private String readGeminiResponse(JsonNode root) {
        JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray()) {
            return "";
        }

        StringJoiner joiner = new StringJoiner(" ");
        for (JsonNode part : parts) {
            String text = part.path("text").asText("").trim();
            if (!text.isEmpty()) {
                joiner.add(text);
            }
        }
        return joiner.toString();
    }

    private String readOllamaResponse(JsonNode root) {
        return root.path("message").path("content").asText("");
    }

    private String readFlexibleTextNode(JsonNode node) {
        if (node.isTextual()) {
            return node.asText("");
        }

        if (!node.isArray()) {
            return "";
        }

        StringJoiner joiner = new StringJoiner(" ");
        for (JsonNode item : node) {
            String text = item.path("text").asText("").trim();
            if (!text.isEmpty()) {
                joiner.add(text);
            }
        }
        return joiner.toString();
    }

    private void sendSplitResponse(CommandSender sender, String response) {
        String prefix = colorize(plugin.getConfig().getString("chat.prefix", "&bAI&7> &r"));
        int maxMessageChars = Math.max(80, plugin.getConfig().getInt("chat.maxCharsPerMessage", 220));
        int chunkSize = Math.max(40, maxMessageChars - ChatColor.stripColor(prefix).length());

        for (String chunk : MessageSplitter.split(response, chunkSize)) {
            sender.sendMessage(prefix + colorize(chunk));
        }
    }

    private AiProviderType getSelectedProvider() {
        String configured = plugin.getConfig().getString("provider", AiProviderType.OPENAI.id());
        return AiProviderType.fromId(configured).orElse(AiProviderType.OPENAI);
    }

    private String buildShortPrompt(String basePrompt) {
        return basePrompt + " Keep each answer under "
            + plugin.getConfig().getInt("chat.preferredReplyChars", 180)
            + " characters when possible. Use multiple short sentences only if needed."
            + " Never ask the player for passwords, API keys, tokens, one-time codes, recovery codes, or payment details."
            + " Do not help with scams, phishing, impersonation, or bypassing account security."
            + " You may use Minecraft color and format codes with '&' such as &a, &b, &c, &6, &l, &o, &n, &m, &k, and &r"
            + " when it helps make the reply clearer or more fun."
            + " You may also use simple symbols like arrows, stars, check marks, and bullets when they fit the message.";
    }

    private String joinUrl(String baseUrl, String path) {
        return stripTrailingSlash(baseUrl) + path;
    }

    private String validateBaseUrl(AiProviderType provider, String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Base URL is not configured for provider " + provider.id());
        }

        URI uri = URI.create(baseUrl.trim());
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null) {
            throw new IllegalStateException("Base URL is invalid for provider " + provider.id());
        }

        if ("https".equalsIgnoreCase(scheme)) {
            return baseUrl.trim();
        }

        if ("http".equalsIgnoreCase(scheme) && isLocalHost(host)) {
            return baseUrl.trim();
        }

        throw new IllegalStateException("Only HTTPS providers are allowed unless the endpoint is local.");
    }

    private boolean isLocalHost(String host) {
        return "localhost".equalsIgnoreCase(host)
            || "127.0.0.1".equals(host)
            || "::1".equals(host);
    }

    private String stripTrailingSlash(String input) {
        return input.endsWith("/") ? input.substring(0, input.length() - 1) : input;
    }

    private String colorize(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    private boolean tryReserveRequestSlot() {
        int maxConcurrentRequests = Math.max(1, plugin.getConfig().getInt("security.maxConcurrentRequests", 8));

        while (true) {
            int current = activeRequests.get();
            if (current >= maxConcurrentRequests) {
                return false;
            }
            if (activeRequests.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    private void releaseRequestSlot() {
        activeRequests.updateAndGet(current -> Math.max(0, current - 1));
    }
}
