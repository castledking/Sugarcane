package io.papermc.paper.sugarcane;

import com.mojang.logging.LogUtils;
import io.papermc.paper.plugin.configuration.PluginMeta;
import java.io.File;
import java.nio.file.Path;
import joptsimple.OptionSet;
import org.bukkit.configuration.file.YamlConfiguration;
import org.slf4j.Logger;

/**
 * Sugarcane's Folia emulation mode.
 * <p>
 * When {@code sugarcane.folia.force-folia} is enabled, Sugarcane refuses to load plugins that do not
 * declare {@code folia-supported: true}, exactly like a real Folia build does. This lets plugin authors
 * develop against the Folia scheduler API on top of Sugarcane's vanilla patches, without needing to run
 * Folia itself and without Paper's main-thread scheduler silently propping up non-Folia code.
 * <p>
 * Plugin providers are built in {@link io.papermc.paper.plugin.PluginInitializerManager#load(OptionSet)},
 * and plugin bootstrappers run immediately after in {@code Bootstrap.bootStrap()} — both well before
 * {@code DedicatedServer} initializes {@link io.papermc.paper.configuration.GlobalConfiguration}. To reject
 * a plugin before any of its code runs we therefore have to read the flag out of paper-global.yml
 * ourselves, the same way {@code PluginInitializerManager} reads the update folder out of bukkit.yml.
 * <p>
 * The values are read once at startup and never change afterwards, so a plugin cannot observe the
 * scheduler changing behaviour underneath it after a {@code /paper reload}.
 */
public final class FoliaMode {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private static final String CONFIG_FILE_NAME = "paper-global.yml";
    private static final String FORCE_FOLIA_PATH = "sugarcane.folia.force-folia";
    private static final String STRICT_SCHEDULER_PATH = "sugarcane.folia.strict-scheduler";

    private static boolean forceFolia = false;
    private static boolean strictScheduler = true;
    private static boolean loaded = false;

    private FoliaMode() {
    }

    /**
     * Reads the Folia mode flags out of paper-global.yml. Called from
     * {@link io.papermc.paper.plugin.PluginInitializerManager#load(OptionSet)} before any plugin provider
     * is built. Subsequent calls are ignored so the mode stays fixed for the server's lifetime.
     */
    public static void loadEarly(final OptionSet options) {
        if (loaded) {
            return;
        }
        loaded = true;

        final Path configDir = ((File) options.valueOf("paper-settings-directory")).toPath();
        final File globalConfig = configDir.resolve(CONFIG_FILE_NAME).toFile();
        if (!globalConfig.isFile()) {
            // First start: the file is written later with the defaults, which are the values already set here.
            return;
        }

        final YamlConfiguration configuration = YamlConfiguration.loadConfiguration(globalConfig);
        forceFolia = configuration.getBoolean(FORCE_FOLIA_PATH, false);
        strictScheduler = configuration.getBoolean(STRICT_SCHEDULER_PATH, true);

        if (forceFolia) {
            LOGGER.info("Sugarcane is running in forced Folia mode: plugins must declare 'folia-supported: true' to load.");
            if (!strictScheduler) {
                LOGGER.info("The legacy BukkitScheduler remains usable because sugarcane.folia.strict-scheduler is disabled.");
            }
        }
    }

    /**
     * Whether plugins are required to declare {@code folia-supported: true}.
     */
    public static boolean forceFolia() {
        return forceFolia;
    }

    /**
     * Whether the legacy {@link org.bukkit.scheduler.BukkitScheduler} should throw
     * {@link UnsupportedOperationException}, as it does on Folia.
     */
    public static boolean strictScheduler() {
        return forceFolia && strictScheduler;
    }

    /**
     * Rejects a plugin that has not opted in to Folia support. Mirrors Folia's own message so build
     * scripts and users see the same text they would on a real Folia server.
     *
     * @throws RuntimeException if forced Folia mode is on and the plugin does not declare support
     */
    public static void checkSupported(final PluginMeta meta) {
        if (forceFolia && !meta.isFoliaSupported()) {
            throw new RuntimeException("Could not load plugin '" + meta.getDisplayName() + "' as it is not marked as supporting Folia!");
        }
    }
}
