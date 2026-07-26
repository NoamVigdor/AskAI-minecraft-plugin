package io.github.opencraftai.minecraftchat.command;

import io.github.opencraftai.minecraftchat.MinecraftAiChatPlugin;
import io.github.opencraftai.minecraftchat.service.AiProviderType;
import io.github.opencraftai.minecraftchat.service.AiService;
import io.github.opencraftai.minecraftchat.service.ConversationStore;
import io.github.opencraftai.minecraftchat.service.SecretsStore;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AiChatCommand implements CommandExecutor, TabCompleter {

    private static final String PRIMARY_COMMAND = "/Ask";
    private static final String LEGACY_COMMAND = "/AI";

    private final MinecraftAiChatPlugin plugin;
    private final AiService aiService;
    private final SecretsStore secretsStore;
    private final ConversationStore conversationStore;
    private final Map<UUID, Long> lastRequestTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<Long>> requestHistory = new ConcurrentHashMap<>();

    public AiChatCommand(
        MinecraftAiChatPlugin plugin,
        AiService aiService,
        SecretsStore secretsStore,
        ConversationStore conversationStore
    ) {
        this.plugin = plugin;
        this.aiService = aiService;
        this.secretsStore = secretsStore;
        this.conversationStore = conversationStore;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if ("ask".equalsIgnoreCase(label)) {
            return handlePrimaryAskCommand(sender, args);
        }

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subcommand) {
            case "ask" -> handleAsk(sender, args);
            case "reset" -> handleReset(sender);
            case "status" -> handleStatus(sender);
            case "api" -> handleRemovedApiCommand(sender, args);
            case "provider" -> handleProvider(sender, args);
            case "model" -> handleModel(sender, args);
            case "reload" -> handleReload(sender);
            default -> {
                sendHelp(sender, label);
                yield true;
            }
        };
    }

    private boolean handlePrimaryAskCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(color("&eUsage: " + PRIMARY_COMMAND + " <message>"));
            return true;
        }

        return handleAskPrompt(sender, String.join(" ", args));
    }

    private boolean handleAsk(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(color("&eUsage: " + LEGACY_COMMAND + " ask <message>"));
            return true;
        }

        return handleAskPrompt(sender, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
    }

    private boolean handleAskPrompt(CommandSender sender, String promptInput) {
        if (!isAskAllowed(sender)) {
            sender.sendMessage(color("&cYou do not have permission to use AskAI."));
            return true;
        }

        String prompt = promptInput.trim();
        if (prompt.isEmpty()) {
            sender.sendMessage(color("&eUsage: " + PRIMARY_COMMAND + " <message>"));
            return true;
        }

        int maxPromptChars = Math.max(50, plugin.getConfig().getInt("security.maxPromptChars", 280));
        if (prompt.length() > maxPromptChars) {
            sender.sendMessage(color("&cThat message is too long. Keep it under &f" + maxPromptChars + "&c characters."));
            return true;
        }

        Player player = sender instanceof Player onlinePlayer ? onlinePlayer : null;
        if (player != null && isCoolingDown(player)) {
            long waitSeconds = getRemainingCooldownSeconds(player);
            sender.sendMessage(color("&cPlease wait &f" + waitSeconds + "&c more second(s) before sending another AI request."));
            return true;
        }

        if (player != null) {
            String quotaMessage = getQuotaMessage(player);
            if (quotaMessage != null) {
                sender.sendMessage(color(quotaMessage));
                return true;
            }
        }

        if (player != null) {
            recordRequest(player);
            lastRequestTimes.put(player.getUniqueId(), System.currentTimeMillis());
        }

        aiService.ask(sender, prompt, player);
        return true;
    }

    private boolean handleReset(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color("&cOnly players can reset chat history."));
            return true;
        }

        conversationStore.clear(player.getUniqueId());
        sender.sendMessage(color("&aYour AI chat history was cleared."));
        return true;
    }

    private boolean handleStatus(CommandSender sender) {
        AiProviderType provider = AiProviderType.fromId(plugin.getConfig().getString("provider"))
            .orElse(AiProviderType.OPENAI);
        String model = plugin.getConfig().getString("model", provider.defaultModel());
        sender.sendMessage(color("&bProvider: &f" + provider.displayName()));
        sender.sendMessage(color("&bModel: &f" + model));
        if (provider.requiresApiKey()) {
            String configured = secretsStore.getApiKey(provider).isBlank() ? "&cmissing" : "&aconfigured";
            sender.sendMessage(color("&bAPI key: " + configured));
        }
        if (hasAdminAccess(sender)) {
            sender.sendMessage(color("&bPlayer access: &f" + enabledText(plugin.getConfig().getBoolean("security.allowPlayerRequests", true))));
            sender.sendMessage(color("&bPrompt limit: &f" + plugin.getConfig().getInt("security.maxPromptChars", 280) + " chars"));
            sender.sendMessage(color("&bCooldown: &f" + plugin.getConfig().getInt("security.requestCooldownSeconds", 3) + "s"));
            sender.sendMessage(color("&bQuota: &f"
                + plugin.getConfig().getInt("security.maxRequestsPerHour", 40) + "/hour, "
                + plugin.getConfig().getInt("security.maxRequestsPerDay", 150) + "/day"));
            sender.sendMessage(color("&bConcurrency: &f" + plugin.getConfig().getInt("security.maxConcurrentRequests", 8)));
            sender.sendMessage(color("&bLinks: &f" + describeLinkPolicy()));
            sender.sendMessage(color("&bObfuscated text: &f"
                + enabledText(plugin.getConfig().getBoolean("outputSafety.allowObfuscatedText", false))));
            sender.sendMessage(color("&bSensitive request filter: &f"
                + enabledText(plugin.getConfig().getBoolean("outputSafety.blockSensitiveRequestPatterns", true))));
        }
        return true;
    }

    private boolean handleRemovedApiCommand(CommandSender sender, String[] args) {
        if (!hasAdminAccess(sender)) {
            sender.sendMessage(color("&cYou do not have permission to manage AI settings."));
            return true;
        }

        sender.sendMessage(color("&cFor security, API keys can no longer be set in chat."));
        sender.sendMessage(color("&7This command is only a warning."));
        sender.sendMessage(color("&7Edit &f"
            + secretsStore.getSecretsFile().getName() + "&7 on the server."));
        return true;
    }

    private boolean handleProvider(CommandSender sender, String[] args) {
        if (!hasAdminAccess(sender)) {
            sender.sendMessage(color("&cYou do not have permission to manage AI settings."));
            return true;
        }

        if (args.length != 2) {
            sender.sendMessage(color("&eUsage: " + LEGACY_COMMAND + " provider <provider>"));
            return true;
        }

        AiProviderType provider = AiProviderType.fromId(args[1]).orElse(null);
        if (provider == null) {
            sender.sendMessage(color("&cUnknown provider. Supported: &f" + supportedProviders()));
            return true;
        }

        plugin.getConfig().set("provider", provider.id());
        plugin.saveConfig();
        sender.sendMessage(color("&aActive provider set to &f" + provider.displayName() + "&a."));
        if (provider.requiresApiKey()) {
            secretsStore.ensureProviderEntry(provider);
            sender.sendMessage(color("&7Set the key in &f" + secretsStore.getSecretsFile().getName()
                + "&7 under &fproviders." + provider.id() + ".apiKey"));
        } else {
            sender.sendMessage(color("&7This provider does not need an API key."));
        }
        return true;
    }

    private boolean handleModel(CommandSender sender, String[] args) {
        if (!hasAdminAccess(sender)) {
            sender.sendMessage(color("&cYou do not have permission to manage AI settings."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(color("&eUsage: " + LEGACY_COMMAND + " model <modelName>"));
            return true;
        }

        String model = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        plugin.getConfig().set("model", model);
        plugin.saveConfig();
        sender.sendMessage(color("&aActive model set to &f" + model + "&a."));
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!hasAdminAccess(sender)) {
            sender.sendMessage(color("&cYou do not have permission to manage AI settings."));
            return true;
        }

        plugin.refreshRuntimeConfig();
        sender.sendMessage(color("&aAskAI reloaded."));
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(color("&bAskAI"));
        sender.sendMessage(color("&f" + PRIMARY_COMMAND + " <message> &7- Ask the AI"));
        sender.sendMessage(color("&7Legacy/admin prefix: &f" + LEGACY_COMMAND));
        sender.sendMessage(color("&f" + LEGACY_COMMAND + " ask <message> &7- Legacy ask command"));
        sender.sendMessage(color("&f" + LEGACY_COMMAND + " reset &7- Clear your chat history"));
        sender.sendMessage(color("&f" + LEGACY_COMMAND + " status &7- Show provider and model"));
        if (hasAdminAccess(sender)) {
            sender.sendMessage(color("&f" + LEGACY_COMMAND + " API &7- Show secure API key setup warning"));
            sender.sendMessage(color("&f" + LEGACY_COMMAND + " provider <provider> &7- Set the active provider"));
            sender.sendMessage(color("&f" + LEGACY_COMMAND + " model <model> &7- Set the active model"));
            sender.sendMessage(color("&f" + LEGACY_COMMAND + " reload &7- Reload the config"));
            sender.sendMessage(color("&7Keys must be set in server-side secrets.yml."));
            sender.sendMessage(color("&7Supported providers: &f" + supportedProviders()));
        }
    }

    private boolean hasAdminAccess(CommandSender sender) {
        return !(sender instanceof Player) || sender.hasPermission("ai.admin");
    }

    private boolean isAskAllowed(CommandSender sender) {
        if (!(sender instanceof Player)) {
            return true;
        }

        if (!plugin.getConfig().getBoolean("security.allowPlayerRequests", true)) {
            return hasAdminAccess(sender);
        }

        return sender.hasPermission("ai.use");
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', Objects.requireNonNullElse(input, ""));
    }

    private String enabledText(boolean enabled) {
        return enabled ? "&aenabled" : "&cdisabled";
    }

    private String supportedProviders() {
        return Arrays.stream(AiProviderType.values())
            .map(AiProviderType::id)
            .reduce((left, right) -> left + ", " + right)
            .orElse("none");
    }

    private String describeLinkPolicy() {
        if (plugin.getConfig().getBoolean("outputSafety.allowLinks", false)) {
            return "&aall allowed";
        }

        List<String> allowedDomains = plugin.getConfig().getStringList("outputSafety.allowedLinkDomains");
        if (allowedDomains.isEmpty()) {
            return "&cblocked";
        }

        return "&eallowlist: &f" + String.join(", ", allowedDomains);
    }

    private boolean isCoolingDown(Player player) {
        return getRemainingCooldownSeconds(player) > 0;
    }

    private long getRemainingCooldownSeconds(Player player) {
        int cooldownSeconds = Math.max(0, plugin.getConfig().getInt("security.requestCooldownSeconds", 3));
        if (cooldownSeconds == 0) {
            return 0;
        }

        Long lastRequestTime = lastRequestTimes.get(player.getUniqueId());
        if (lastRequestTime == null) {
            return 0;
        }

        long remainingMillis = (lastRequestTime + (cooldownSeconds * 1000L)) - System.currentTimeMillis();
        if (remainingMillis <= 0) {
            return 0;
        }

        return (remainingMillis + 999L) / 1000L;
    }

    private String getQuotaMessage(Player player) {
        Deque<Long> history = getRecentHistory(player);
        int maxRequestsPerHour = Math.max(0, plugin.getConfig().getInt("security.maxRequestsPerHour", 40));
        if (maxRequestsPerHour > 0 && countSince(history, System.currentTimeMillis() - 3_600_000L) >= maxRequestsPerHour) {
            return "&cYou reached the hourly AI limit. Please try again later.";
        }

        int maxRequestsPerDay = Math.max(0, plugin.getConfig().getInt("security.maxRequestsPerDay", 150));
        if (maxRequestsPerDay > 0 && countSince(history, System.currentTimeMillis() - 86_400_000L) >= maxRequestsPerDay) {
            return "&cYou reached the daily AI limit. Please try again tomorrow.";
        }

        return null;
    }

    private void recordRequest(Player player) {
        Deque<Long> history = getRecentHistory(player);
        synchronized (history) {
            history.addLast(System.currentTimeMillis());
        }
    }

    private Deque<Long> getRecentHistory(Player player) {
        Deque<Long> history = requestHistory.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayDeque<>());
        long cutoff = System.currentTimeMillis() - 86_400_000L;
        synchronized (history) {
            while (!history.isEmpty() && history.peekFirst() < cutoff) {
                history.removeFirst();
            }
            return history;
        }
    }

    private int countSince(Deque<Long> history, long cutoff) {
        synchronized (history) {
            int count = 0;
            for (Long timestamp : history) {
                if (timestamp >= cutoff) {
                    count++;
                }
            }
            return count;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if ("ask".equalsIgnoreCase(alias)) {
            return List.of();
        }

        if (args.length == 1) {
            List<String> base = List.of("ask", "reset", "status");
            if (!hasAdminAccess(sender)) {
                return filter(base, args[0]);
            }
            return filter(List.of("ask", "reset", "status", "API", "provider", "model", "reload"), args[0]);
        }

        if (args.length == 2 && "provider".equalsIgnoreCase(args[0])) {
            return filter(Arrays.stream(AiProviderType.values()).map(AiProviderType::id).toList(), args[1]);
        }

        return List.of();
    }

    private List<String> filter(List<String> values, String current) {
        String normalized = current.toLowerCase(Locale.ROOT);
        return values.stream()
            .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized))
            .toList();
    }
}
