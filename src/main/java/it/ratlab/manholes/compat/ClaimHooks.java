// SPDX-License-Identifier: MIT
package it.ratlab.manholes.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Land-claim integration (FTB Chunks). {@link #NONE} when no claim mod is loaded. */
public interface ClaimHooks {
    ClaimHooks NONE = (level, pos) -> false;

    /** True when the chunk at {@code pos} is claimed by anyone. */
    boolean isClaimed(ServerLevel level, BlockPos pos);
}
