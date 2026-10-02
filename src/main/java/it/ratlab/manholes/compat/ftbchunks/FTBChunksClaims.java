// SPDX-License-Identifier: MIT
package it.ratlab.manholes.compat.ftbchunks;

import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import it.ratlab.manholes.compat.ClaimHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Server side FTB Chunks: a chunk is claimed when the claim manager has an entry for it. Only loaded with FTB Chunks. */
public final class FTBChunksClaims implements ClaimHooks {
    @Override
    public boolean isClaimed(ServerLevel level, BlockPos pos) {
        FTBChunksAPI.API api = FTBChunksAPI.api();
        return api != null && api.isManagerLoaded() && api.getManager().getChunk(new ChunkDimPos(level, pos)) != null;
    }
}
