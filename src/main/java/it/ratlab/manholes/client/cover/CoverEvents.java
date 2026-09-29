// SPDX-License-Identifier: MIT
package it.ratlab.manholes.client.cover;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Mod-bus hooks of the cover looks (the loader methods stay package-private). */
public final class CoverEvents {
    private CoverEvents() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(ModelEvent.RegisterAdditional.class, CoverLooks::onRegisterAdditional);
        modBus.addListener(ModelEvent.BakingCompleted.class, CoverLooks::onBakingCompleted);
    }
}
