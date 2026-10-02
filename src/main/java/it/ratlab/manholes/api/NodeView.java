// SPDX-License-Identifier: MIT
package it.ratlab.manholes.api;

import it.ratlab.manholes.data.NodeRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Read-only view of a node for scripts: {@code node.id}, {@code node.name}, {@code node.pos}, ... */
public final class NodeView {
    private final NodeRecord record;

    public NodeView(NodeRecord record) {
        this.record = record;
    }

    public NodeView(NodeRecord record, @Nullable Object ignoredRegistries) {
        this.record = record;
    }

    @Nullable
    public static NodeView of(@Nullable NodeRecord r) {
        return r == null ? null : new NodeView(r);
    }

    @Nullable
    public static NodeView of(@Nullable NodeRecord r, @Nullable Object ignoredRegistries) {
        return r == null ? null : new NodeView(r);
    }

    public NodeRecord record() {
        return record;
    }

    public String getId() {
        return record.id.toString();
    }

    public String getShortId() {
        return record.shortId();
    }

    /** Display name as plain text. */
    public String getName() {
        return record.displayName().getString();
    }

    public Component getNameComponent() {
        return record.displayName();
    }

    public String getDimension() {
        return record.dimension.location().toString();
    }

    public BlockPos getPos() {
        return record.pos;
    }

    public int getX() {
        return record.pos.getX();
    }

    public int getY() {
        return record.pos.getY();
    }

    public int getZ() {
        return record.pos.getZ();
    }

    /** Spawn rule id, or null for hand-placed nodes. */
    @Nullable
    public String getRuleId() {
        return record.ruleId.isEmpty() ? null : record.ruleId;
    }

    @Nullable
    public String getStructureId() {
        return record.structureId.isEmpty() ? null : record.structureId;
    }

    public boolean isHome() {
        return record.home;
    }

    public boolean isGenerated() {
        return record.generated;
    }

    /** Home manholes (1.5.0): the owner's uuid as a string, or null (world cover / unowned home). */
    @Nullable
    public String getOwner() {
        return record.owner == null ? null : record.owner.toString();
    }

    /** Home manholes: the owner's name ("" = unknown / unowned). */
    public String getOwnerName() {
        return record.ownerName;
    }

    /** Home manholes: shared with the owner's team (unowned homes count as shared). False for world covers. */
    public boolean isShared() {
        return record.home && (record.owner == null || record.shared);
    }

    /** Look id ({@code city}, {@code home_manhole}, {@code hatch}, ...). */
    public String getLook() {
        return record.lookOrDefault();
    }

    /** Stage given when this node is opened. */
    public String getStage() {
        return record.stageName();
    }

    @Override
    public String toString() {
        return "ManholeNode[" + record.shortId() + " '" + getName() + "' " + getDimension() + " " + record.pos.toShortString() + "]";
    }
}
