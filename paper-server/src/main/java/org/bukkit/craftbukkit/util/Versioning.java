package org.bukkit.craftbukkit.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.SharedConstants;
import org.bukkit.Bukkit;

public final class Versioning {
    private static final String BUKKIT_VERSION;
    private static final String API_VERSION;

    static {
        String bukkitVersion = "Unknown-Version";
        String apiVersion = null;
        try (final InputStream stream = Bukkit.class.getClassLoader().getResourceAsStream("apiVersioning.json")) {
            if (stream == null) {
                throw new IOException("apiVersioning.json not found in classpath");
            }

            final JsonObject jsonObject = new Gson()
                .fromJson(new BufferedReader(new InputStreamReader(stream)), JsonObject.class);

            if (jsonObject == null) {
                throw new IOException("apiVersioning.json is not a valid JSON file");
            }

            bukkitVersion = jsonObject.get("version").getAsString();
            apiVersion = jsonObject.get("currentApiVersion").getAsString();
        } catch (final IOException ex) {
            Logger.getLogger(Versioning.class.getName()).log(Level.SEVERE, "Could not get Bukkit version!", ex);
        }
        if (apiVersion == null) {
            apiVersion = SharedConstants.getCurrentVersion().id();
        }
        API_VERSION = apiVersion;
        // Sugarcane start - report the historical Bukkit version format
        // Paper 26.x versions the API artifact "<mc>.build.<number>-<channel>", and that is the string
        // Bukkit#getBukkitVersion hands to plugins. Anything reading it the way plugins always have - split
        // on '-', take the first token - gets "26.1.2.build.9" where it expects a Minecraft version, and then
        // fails its version lookup or reports nonsense (bStats charts, AdvancedEnchantments' bundled NBT-API
        // logging "Found Minecraft: 26.1.2.build.9"). Report "<mc>-R0.1-SNAPSHOT" instead, which is what every
        // release up to 26.1 reported. Nothing is lost: the build number and channel are still in
        // Bukkit#getVersion, ServerBuildInfo and the jar manifest.
        BUKKIT_VERSION = bukkitVersion.endsWith("-R0.1-SNAPSHOT") ? bukkitVersion : apiVersion + "-R0.1-SNAPSHOT";
        // Sugarcane end - report the historical Bukkit version format
    }

    public static String getBukkitVersion() {
        return BUKKIT_VERSION;
    }

    public static String getCurrentApiVersion() {
        return API_VERSION;
    }
}
