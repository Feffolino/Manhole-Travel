// SPDX-License-Identifier: MIT
package it.ratlab.manholes.client.cover;

import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/** Mod-bus hooks of the cover looks (the loader methods stay package-private). */
public final class CoverEvents {
    private CoverEvents() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(CoverLooks::onRegisterAdditional);
        modBus.addListener(CoverLooks::onBakingCompleted);
    }
}
