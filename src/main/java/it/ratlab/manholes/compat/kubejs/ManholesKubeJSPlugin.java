// SPDX-License-Identifier: MIT
package it.ratlab.manholes.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;

/** Listed in kubejs.plugins.txt; KubeJS only reads that file when KubeJS itself is installed. */
public class ManholesKubeJSPlugin implements KubeJSPlugin {
    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(ManholeEventsJS.GROUP);
    }

    @Override
    public void registerBindings(BindingRegistry bindings) {
        if (bindings.type().isServer()) {
            bindings.add("Manholes", new ManholesBindingJS());
        }
    }
}
