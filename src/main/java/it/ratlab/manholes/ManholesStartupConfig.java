// SPDX-License-Identifier: MIT
package it.ratlab.manholes;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

/**
 * Startup config: {@code config/manholes-startup.toml}. Read manually on startup before items are registered.
 */
public final class ManholesStartupConfig {
    private static int crowbarDurability = 250;

    private ManholesStartupConfig() {}

    public static void load() {
        try {
            Path path = FMLPaths.CONFIGDIR.get().resolve("manholes-startup.toml");
            CommentedFileConfig config = CommentedFileConfig.builder(path)
                    .sync()
                    .autosave()
                    .writingMode(WritingMode.REPLACE)
                    .build();
            if (!path.toFile().exists()) {
                config.set("crowbar.crowbarDurability", 250);
                config.setComment("crowbar", "Durability of manholes:crowbar. Read when items are registered: restart the game after changing it.");
                config.save();
            } else {
                config.load();
                Number val = config.get("crowbar.crowbarDurability");
                if (val != null) {
                    crowbarDurability = Math.max(1, Math.min(100_000, val.intValue()));
                }
            }
            config.close();
        } catch (Exception e) {
            Manholes.LOGGER.warn("Failed to read manholes-startup.toml, using default 250", e);
        }
    }

    public static int crowbarDurability() {
        return crowbarDurability;
    }
}
