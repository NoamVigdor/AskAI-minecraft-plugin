package io.github.opencraftai.minecraftchat;

import io.github.opencraftai.minecraftchat.command.AiChatCommand;
import io.github.opencraftai.minecraftchat.service.AiService;
import io.github.opencraftai.minecraftchat.service.ConversationStore;
import io.github.opencraftai.minecraftchat.service.SecretsStore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MinecraftAiChatPlugin extends JavaPlugin {

    private final ExecutorService requestExecutor = Executors.newFixedThreadPool(4);

    private SecretsStore secretsStore;
    private ConversationStore conversationStore;
    private AiService aiService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.secretsStore = new SecretsStore(this);
        this.secretsStore.load();

        this.conversationStore = new ConversationStore(getConversationPairs());
        this.aiService = new AiService(this, secretsStore, conversationStore, requestExecutor);

        AiChatCommand commandExecutor = new AiChatCommand(this, aiService, secretsStore, conversationStore);
        PluginCommand command = Objects.requireNonNull(getCommand("ask"), "ask command missing");
        command.setExecutor(commandExecutor);
        command.setTabCompleter(commandExecutor);

        playStartupAnimation();
    }

    @Override
    public void onDisable() {
        requestExecutor.shutdownNow();
    }

    public void refreshRuntimeConfig() {
        reloadConfig();
        conversationStore.setMaxConversationPairs(getConversationPairs());
    }

    private int getConversationPairs() {
        return Math.max(1, getConfig().getInt("conversationPairs", 6));
    }

    private void playStartupAnimation() {
        String[] frames = {
            "&8&m------------------------------------------------",
            "&b   ___          _    ",
            "&b  / _ | ___ ___| |__ &7Loading AskAI...",
            "&3 / __ |(_-</ / / / / &7Providers ready",
            "&b/_/ |_|/___/_/_/_/_/ &7Use &f/Ask <message> &7or &f/AI",
            "&8&m------------------------------------------------"
        };

        for (int i = 0; i < frames.length; i++) {
            final String frame = frames[i];
            Bukkit.getScheduler().runTaskLater(this, () ->
                Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', frame)),
                i * 4L
            );
        }

        Bukkit.getScheduler().runTaskLater(this, () ->
            Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes(
                '&',
                "&aAskAI v" + getDescription().getVersion() + " enabled."
            )),
            frames.length * 4L
        );
    }
}
