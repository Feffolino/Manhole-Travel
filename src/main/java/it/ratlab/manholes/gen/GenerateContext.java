// SPDX-License-Identifier: MIT
package it.ratlab.manholes.gen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/** A spot chosen by a spawn rule, before placing. Scripts may move it, rename it or cancel it. */
public final class GenerateContext {
    public final ServerLevel level;
    public BlockPos pos;
    public final String ruleId;
    @Nullable
    public final String structureId;
    /** Raw name (plain text or JSON component), null = default naming. */
    @Nullable
    public String name;

    public GenerateContext(ServerLevel level, BlockPos pos, String ruleId, @Nullable String structureId, @Nullable String name) {
        this.level = level;
        this.pos = pos;
        this.ruleId = ruleId;
        this.structureId = structureId;
        this.name = name;
    }
}
