// SPDX-License-Identifier: MIT
package it.ratlab.manholes.client.cover;

import it.ratlab.manholes.block.ManholeBlock;
import it.ratlab.manholes.client.ManholesClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Mod-bus hooks of the cover looks (the loader methods stay package-private). */
public final class CoverEvents {
    private CoverEvents() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(ModelEvent.RegisterAdditional.class, CoverLooks::onRegisterAdditional);
        modBus.addListener(ModelEvent.ModifyBakingResult.class, CoverEvents::wrapCoverModels);
        modBus.addListener(ModelEvent.BakingCompleted.class, CoverLooks::onBakingCompleted);
        // 1.7.2: the static covers are in the chunk mesh, so a changed showConditionOverlays needs a re-mesh.
        modBus.addListener(ModConfigEvent.Reloading.class, e -> {
            if (e.getConfig().getSpec() == ManholesClientConfig.SPEC) {
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> {
                    if (mc.level != null) {
                        mc.levelRenderer.allChanged();
                    }
                });
            }
        });
    }

    /** 1.7.2: every cover blockstate model becomes a {@link CoverBakedModel} (static covers in the chunk mesh). */
    private static void wrapCoverModels(ModelEvent.ModifyBakingResult event) {
        var models = event.getModels();
        for (ManholeBlock block : ManholeBlock.all()) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                models.computeIfPresent(BlockModelShaper.stateToModelLocation(state),
                        (k, m) -> m instanceof CoverBakedModel ? m : (BakedModel) new CoverBakedModel(m));
            }
        }
    }
}
