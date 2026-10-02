// SPDX-License-Identifier: MIT
package it.ratlab.manholes.travel;

public final class NetworkSync {
    private static volatile boolean dirty;

    private NetworkSync() {}

    public static void markDirty() {
        dirty = true;
    }
}
