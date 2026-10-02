// SPDX-License-Identifier: MIT
package it.ratlab.manholes.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import it.ratlab.manholes.api.ManholesAPI;
import it.ratlab.manholes.data.ManholeData;
import it.ratlab.manholes.data.NodeRecord;
import it.ratlab.manholes.gen.WorldGenHandler;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.RegisterCommandsEvent;

public final class ManholeCommands {
    private static final SimpleCommandExceptionType NO_NODE = new SimpleCommandExceptionType(Component.translatable("manholes.command.no_node"));

    private static final SuggestionProvider<CommandSourceStack> NODES = (ctx, b) -> {
        List<String> ids = new ArrayList<>();
        ids.add("here");
        for (NodeRecord r : ManholeData.get(ctx.getSource().getServer()).nodes()) {
            ids.add(r.shortId());
        }
        return SharedSuggestionProvider.suggest(ids, b);
    };

    private static final SuggestionProvider<CommandSourceStack> COVER_BLOCKS = (ctx, b) -> SharedSuggestionProvider.suggest(
            it.ratlab.manholes.block.ManholeBlock.all().stream().map(it.ratlab.manholes.block.ManholeBlock::id).toList(), b);

    private ManholeCommands() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        java.util.function.Predicate<CommandSourceStack> op = s -> s.hasPermission(2);
        d.register(Commands.literal("manholes")
                .then(Commands.literal("open").requires(op).then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("node", StringArgumentType.word()).suggests(NODES).executes(c -> open(c, true)))))
                .then(Commands.literal("close").requires(op).then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("node", StringArgumentType.word()).suggests(NODES).executes(c -> open(c, false)))))
                .then(Commands.literal("list").requires(op)
                        .executes(c -> list(c, c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> list(c, EntityArgument.getPlayer(c, "player")))))
                .then(Commands.literal("tp").requires(op).then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("node", StringArgumentType.word()).suggests(NODES).executes(ManholeCommands::tp))))
                .then(Commands.literal("name").requires(op).then(Commands.argument("node", StringArgumentType.word()).suggests(NODES)
                        .then(Commands.argument("text", StringArgumentType.greedyString()).executes(ManholeCommands::name))))
                .then(Commands.literal("setblock").requires(op).then(Commands.argument("node", StringArgumentType.word()).suggests(NODES)
                        .then(Commands.argument("block", StringArgumentType.greedyString()).suggests(COVER_BLOCKS)
                                .executes(ManholeCommands::setBlock))))
                .then(Commands.literal("share").then(Commands.argument("node", StringArgumentType.word()).suggests(NODES)
                        .then(Commands.argument("shared", com.mojang.brigadier.arguments.BoolArgumentType.bool())
                                .executes(ManholeCommands::share))))
                .then(Commands.literal("debug").requires(op).then(Commands.literal("nearby").executes(ManholeCommands::nearby)))
                .then(Commands.literal("regen").requires(op).then(Commands.literal("here").executes(ManholeCommands::regen))));
    }

    private static int share(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        NodeRecord r = node(c);
        boolean shared = com.mojang.brigadier.arguments.BoolArgumentType.getBool(c, "shared");
        if (!r.home) {
            c.getSource().sendFailure(Component.translatable("manholes.command.share_not_home", describe(c.getSource(), r)));
            return 0;
        }
        ServerPlayer actor = c.getSource().getPlayer();
        if (actor != null && !it.ratlab.manholes.travel.Access.canManage(actor, r)) {
            c.getSource().sendFailure(Component.translatable("manholes.command.share_not_owner"));
            return 0;
        }
        it.ratlab.manholes.travel.HomeManholes.setShared(c.getSource().getServer(), null, r, shared);
        c.getSource().sendSuccess(() -> Component.translatable(shared ? "manholes.command.shared" : "manholes.command.unshared",
                describe(c.getSource(), r)), true);
        return 1;
    }

    private static NodeRecord node(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String arg = StringArgumentType.getString(c, "node");
        NodeRecord r;
        if ("here".equalsIgnoreCase(arg)) {
            r = ManholesAPI.nearest(c.getSource().getLevel(), BlockPos.containing(c.getSource().getPosition()), 4.0);
        } else {
            r = ManholesAPI.resolve(c.getSource().getServer(), arg);
        }
        if (r == null) {
            throw NO_NODE.create();
        }
        return r;
    }

    private static Component describe(CommandSourceStack src, NodeRecord r) {
        return Component.translatable("manholes.command.node", r.shortId(), r.displayName(),
                r.dimension.location().toString(), r.pos.getX(), r.pos.getY(), r.pos.getZ(),
                r.ruleId.isEmpty() ? (r.home ? "home" : "-") : r.ruleId);
    }

    private static int open(CommandContext<CommandSourceStack> c, boolean open) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, "player");
        NodeRecord r = node(c);
        boolean changed = open ? ManholesAPI.open(p, r) : ManholesAPI.close(p, r);
        c.getSource().sendSuccess(() -> Component.translatable(open ? "manholes.command.opened" : "manholes.command.closed",
                describe(c.getSource(), r), p.getDisplayName()), true);
        return changed ? 1 : 0;
    }

    private static int list(CommandContext<CommandSourceStack> c, ServerPlayer p) {
        List<NodeRecord> nodes = ManholesAPI.network(p);
        c.getSource().sendSuccess(() -> Component.translatable("manholes.command.list", p.getDisplayName(), nodes.size()), false);
        for (NodeRecord r : nodes) {
            c.getSource().sendSuccess(() -> describe(c.getSource(), r), false);
        }
        return nodes.size();
    }

    private static int tp(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, "player");
        NodeRecord r = node(c);
        if (!ManholesAPI.travel(p, r)) {
            throw NO_NODE.create();
        }
        c.getSource().sendSuccess(() -> Component.translatable("manholes.command.tp", p.getDisplayName(), describe(c.getSource(), r)), true);
        return 1;
    }

    private static int name(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        NodeRecord r = node(c);
        String text = StringArgumentType.getString(c, "text");
        ManholesAPI.rename(c.getSource().getServer(), r, text);
        c.getSource().sendSuccess(() -> Component.translatable("manholes.command.named", describe(c.getSource(), r)), true);
        return 1;
    }

    private static int setBlock(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        NodeRecord r = node(c);
        String block = StringArgumentType.getString(c, "block").strip();
        ServerLevel level = c.getSource().getServer().getLevel(r.dimension);
        if (level == null || !ManholesAPI.setBlock(level, r.pos, block)) {
            c.getSource().sendFailure(Component.translatable("manholes.command.setblock_failed", block));
            return 0;
        }
        net.minecraft.world.level.block.Block now = level.getBlockState(r.pos).getBlock();
        c.getSource().sendSuccess(() -> Component.translatable("manholes.command.setblock", describe(c.getSource(), r),
                now.getName()), true);
        return 1;
    }

    private static int nearby(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getLevel();
        BlockPos at = BlockPos.containing(c.getSource().getPosition());
        List<NodeRecord> found = new ArrayList<>();
        for (NodeRecord r : ManholeData.get(c.getSource().getServer()).nodes()) {
            if (r.generated && r.dimension.equals(level.dimension()) && r.pos.distSqr(at) <= 512.0 * 512.0) {
                found.add(r);
            }
        }
        found.sort(Comparator.comparingDouble(r -> r.pos.distSqr(at)));
        c.getSource().sendSuccess(() -> Component.translatable("manholes.command.nearby", found.size()), false);
        for (NodeRecord r : found) {
            int dist = (int) Math.sqrt(r.pos.distSqr(at));
            c.getSource().sendSuccess(() -> Component.translatable("manholes.command.nearby_entry", describe(c.getSource(), r), dist), false);
        }
        return found.size();
    }

    private static int regen(CommandContext<CommandSourceStack> c) {
        ServerLevel level = c.getSource().getLevel();
        ChunkPos cp = new ChunkPos(BlockPos.containing(c.getSource().getPosition()));
        int before = ManholeData.get(level.getServer()).nodes().size();
        int jobs = WorldGenHandler.regenChunk(level, cp);
        int placed = ManholeData.get(level.getServer()).nodes().size() - before;
        c.getSource().sendSuccess(() -> Component.translatable("manholes.command.regen", cp.x, cp.z, jobs, placed), true);
        return placed;
    }
}
