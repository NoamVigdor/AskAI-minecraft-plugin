package io.github.opencraftai.minecraftchat.service;

import io.github.opencraftai.minecraftchat.MinecraftAiChatPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

public final class SecretsStore {

    private final MinecraftAiChatPlugin plugin;
    private final File secretsFile;
    private YamlConfiguration secretsConfig;

    public SecretsStore(MinecraftAiChatPlugin plugin) {
        this.plugin = plugin;
        this.secretsFile = new File(plugin.getDataFolder(), "secrets.yml");
    }

    public void load() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder.");
        }

        if (!secretsFile.exists()) {
            try {
                if (!secretsFile.createNewFile()) {
                    plugin.getLogger().warning("Could not create secrets.yml");
                }
            } catch (IOException exception) {
                plugin.getLogger().warning("Could not create secrets.yml: " + exception.getMessage());
            }
        }

        applyBestEffortFileProtection();
        this.secretsConfig = YamlConfiguration.loadConfiguration(secretsFile);
        ensureProviderTemplate();
    }

    public String getApiKey(AiProviderType provider) {
        return secretsConfig.getString("providers." + provider.id() + ".apiKey", "").trim();
    }

    public File getSecretsFile() {
        return secretsFile;
    }

    public boolean ensureProviderEntry(AiProviderType provider) {
        if (!provider.requiresApiKey()) {
            return false;
        }

        String path = "providers." + provider.id() + ".apiKey";
        if (secretsConfig.contains(path)) {
            return false;
        }

        secretsConfig.set(path, "");
        return saveSecretsConfig("Could not update secrets.yml with provider template: ");
    }

    private void applyBestEffortFileProtection() {
        try {
            if (!secretsFile.setReadable(false, false)) {
                plugin.getLogger().fine("Could not clear global read access on secrets.yml");
            }
            if (!secretsFile.setReadable(true, true)) {
                plugin.getLogger().fine("Could not set owner read access on secrets.yml");
            }
            if (!secretsFile.setWritable(false, false)) {
                plugin.getLogger().fine("Could not clear global write access on secrets.yml");
            }
            if (!secretsFile.setWritable(true, true)) {
                plugin.getLogger().fine("Could not set owner write access on secrets.yml");
            }
        } catch (SecurityException exception) {
            plugin.getLogger().warning("Could not apply local file protections to secrets.yml: "
                + Objects.requireNonNullElse(exception.getMessage(), "unknown reason"));
        }
    }

    private void ensureProviderTemplate() {
        boolean changed = false;

        if (!secretsConfig.contains("providers")) {
            secretsConfig.createSection("providers");
            changed = true;
        }

        for (AiProviderType provider : AiProviderType.values()) {
            if (!provider.requiresApiKey()) {
                continue;
            }

            String path = "providers." + provider.id() + ".apiKey";
            if (!secretsConfig.contains(path)) {
                secretsConfig.set(path, "");
                changed = true;
            }
        }

        if (!changed) {
            return;
        }

        saveSecretsConfig("Could not update secrets.yml template: ");
    }

    private boolean saveSecretsConfig(String errorPrefix) {
        try {
            secretsConfig.save(secretsFile);
            applyBestEffortFileProtection();
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning(errorPrefix + exception.getMessage());
            return false;
        }
    }
}
