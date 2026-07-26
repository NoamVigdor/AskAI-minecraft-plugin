package io.github.opencraftai.minecraftchat.service;

import io.github.opencraftai.minecraftchat.MinecraftAiChatPlugin;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class AiSafetyFilter {

    private static final Pattern URL_PATTERN = Pattern.compile("(?i)\\bhttps?://\\S+");
    private static final Pattern OBFUSCATED_CODE_PATTERN = Pattern.compile("(?i)[&§]k");
    private static final List<Pattern> SENSITIVE_REQUEST_PATTERNS = List.of(
        Pattern.compile("(?i)\\b(send|give|share|paste|tell)\\b.{0,30}\\b(password|passcode|pin)\\b"),
        Pattern.compile("(?i)\\b(send|give|share|paste|tell)\\b.{0,30}\\b(api key|token|secret|session cookie|otp|2fa|recovery code|seed phrase)\\b"),
        Pattern.compile("(?i)\\b(credit card|debit card|bank account|social security|ssn)\\b")
    );

    private final MinecraftAiChatPlugin plugin;

    public AiSafetyFilter(MinecraftAiChatPlugin plugin) {
        this.plugin = plugin;
    }

    public String filter(String response) {
        String filtered = response == null ? "" : response.replace('§', '&');

        filtered = filterLinks(filtered);

        if (!plugin.getConfig().getBoolean("outputSafety.allowObfuscatedText", false)) {
            filtered = OBFUSCATED_CODE_PATTERN.matcher(filtered).replaceAll("&7");
        }

        if (plugin.getConfig().getBoolean("outputSafety.blockSensitiveRequestPatterns", true)
            && containsSensitiveRequest(filtered)) {
            return plugin.getConfig().getString(
                "outputSafety.blockedResponseMessage",
                "&cI can't help ask for passwords, tokens, or other sensitive account data."
            );
        }

        return filtered.trim();
    }

    private String filterLinks(String response) {
        if (plugin.getConfig().getBoolean("outputSafety.allowLinks", false)) {
            return response;
        }

        String replacement = plugin.getConfig().getString("outputSafety.removedLinkText", "[link removed]");
        List<String> allowedDomains = plugin.getConfig().getStringList("outputSafety.allowedLinkDomains").stream()
            .map(domain -> domain.toLowerCase(Locale.ROOT).trim())
            .filter(domain -> !domain.isBlank())
            .toList();

        return URL_PATTERN.matcher(response).replaceAll(matchResult -> {
            String url = matchResult.group();
            return isAllowedLink(url, allowedDomains) ? url : replacement;
        });
    }

    private boolean containsSensitiveRequest(String response) {
        String lowered = response.toLowerCase(Locale.ROOT);
        for (Pattern pattern : SENSITIVE_REQUEST_PATTERNS) {
            if (pattern.matcher(lowered).find()) {
                return true;
            }
        }
        return false;
    }

    private boolean isAllowedLink(String url, List<String> allowedDomains) {
        if (allowedDomains.isEmpty()) {
            return false;
        }

        try {
            String host = Objects.requireNonNullElse(URI.create(url).getHost(), "").toLowerCase(Locale.ROOT);
            if (host.isBlank()) {
                return false;
            }

            for (String allowedDomain : allowedDomains) {
                if (host.equals(allowedDomain) || host.endsWith("." + allowedDomain)) {
                    return true;
                }
            }
        } catch (IllegalArgumentException ignored) {
            return false;
        }

        return false;
    }
}
