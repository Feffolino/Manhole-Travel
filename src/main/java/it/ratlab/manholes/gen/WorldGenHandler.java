// SPDX-License-Identifier: MIT
package it.ratlab.manholes.gen;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.eventbus.api.IEventBus;

public final class WorldGenHandler {
    private WorldGenHandler() {}

    public static void registerEvents(IEventBus bus) {
    }

    public static int regenChunk(ServerLevel level, ChunkPos cp) {
        return 0;
    }
}
