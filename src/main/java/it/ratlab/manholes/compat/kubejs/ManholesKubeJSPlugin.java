// SPDX-License-Identifier: MIT
package it.ratlab.manholes.compat.kubejs;

import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;

/** Listed in kubejs.plugins.txt; KubeJS only reads that file when KubeJS itself is installed. */
public class ManholesKubeJSPlugin extends KubeJSPlugin {
    @Override
    public void registerEvents() {
        ManholeEventsJS.GROUP.register();
    }

    @Override
    public void registerBindings(BindingsEvent bindings) {
        if (bindings.getType().isServer()) {
            bindings.add("Manholes", new ManholesBindingJS());
        }
    }
}
