// SPDX-License-Identifier: MIT
package it.ratlab.manholes.test;

import it.ratlab.manholes.Manholes;
import it.ratlab.manholes.ModRegistry;
import it.ratlab.manholes.api.ManholesAPI;
import it.ratlab.manholes.block.ManholeBlockEntity;
import it.ratlab.manholes.data.ManholeData;
import it.ratlab.manholes.data.Names;
import it.ratlab.manholes.data.NodeRecord;
import it.ratlab.manholes.gen.SpawnRuleManager;
import it.ratlab.manholes.ManholesConfig;
import it.ratlab.manholes.ManholesStartupConfig;
import it.ratlab.manholes.travel.Access;
import it.ratlab.manholes.travel.HomeManholes;
import it.ratlab.manholes.travel.ManholeInteraction;
import it.ratlab.manholes.travel.PryHandler;
import it.ratlab.manholes.travel.PryParams;
import it.ratlab.manholes.travel.TravelHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import it.ratlab.manholes.block.ManholeBlock;
import it.ratlab.manholes.data.ManholeData.PendingPlacement;
import it.ratlab.manholes.gen.WorldGenHandler;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Headless checks run by {@code ./gradlew runGameTestServer} (only registered with -Dmanholes.gametests=true).
 * They use the NBT sample of the test datapack ({@code manholes_test:manhole_sample}) as template, so the test datapack
 * must be in the game test world's datapacks folder.
 */
@GameTestHolder(Manholes.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ManholeGameTests {
    private static final String NS = "manholes_test";
    private static final String TEMPLATE = "manhole_sample";
    /** Where the sample NBT has its named manhole (game test coordinates are one block above the template origin). */
    private static final BlockPos SAMPLE_MANHOLE = new BlockPos(1, 2, 1);

    /** The optional second run (gameTestServerKubeJS) has KubeJS + Rhino in run-kubejs/mods and the example script. */
    private static final boolean KUBEJS = net.neoforged.fml.ModList.get().isLoaded("kubejs");

    private ManholeGameTests() {}

    /** Mock player in the player list; with KubeJS a FakePlayer (KubeJS login packets break the mock connection). */
    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper h) {
        return KUBEJS ? net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel()) : h.makeMockServerPlayerInLevel();
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterGameTestsEvent e) -> e.register(ManholeGameTests.class));
    }

    /** The test datapack's structure + scatter rules were loaded (and built-ins skipped: naturalSpawn=false). */
    @GameTest(templateNamespace = NS, template = TEMPLATE)
    public static void spawnRulesLoaded(GameTestHelper h) {
        h.assertTrue(SpawnRuleManager.ids().contains(ResourceLocation.parse("manholes_test:test_villages")), "structure rule missing: " + SpawnRuleManager.ids());
        if (KUBEJS) {
            // The example script (kubejs/server_scripts/manholes_example.js) adds three rules and removes the scatter one.
            h.assertTrue(SpawnRuleManager.ids().contains(ResourceLocation.parse("kubejs:example_village_sewer")), "builder rule missing: " + SpawnRuleManager.ids());
            h.assertTrue(SpawnRuleManager.ids().contains(ResourceLocation.parse("kubejs:example_roads")), "scatter builder rule missing: " + SpawnRuleManager.ids());
            h.assertTrue(SpawnRuleManager.ids().contains(ResourceLocation.parse("kubejs:example_outpost")), "json rule missing: " + SpawnRuleManager.ids());
            h.assertFalse(SpawnRuleManager.ids().contains(ResourceLocation.parse("manholes_test:test_scatter")), "event.remove ignored: " + SpawnRuleManager.ids());
        } else {
            h.assertTrue(SpawnRuleManager.ids().contains(ResourceLocation.parse("manholes_test:test_scatter")), "scatter rule missing: " + SpawnRuleManager.ids());
        }
        h.assertValueEqual(SpawnRuleManager.ids().contains(ResourceLocation.parse("manholes:villages")),
                ManholesConfig.b(ManholesConfig.NATURAL_SPAWN), "built-in rule active == naturalSpawn");
        h.assertValueEqual(SpawnRuleManager.byId().get(ResourceLocation.parse("manholes_test:test_villages")).look(), "hatch", "JSON rule look");
        if (KUBEJS) {
            h.assertValueEqual(SpawnRuleManager.byId().get(ResourceLocation.parse("kubejs:example_village_sewer")).look(), "hatch",
                    "builder .look()");
        }
        h.succeed();
    }

    /** A manhole from an NBT structure gets a node id on load and keeps the NBT name (naming priority 1). */
    @GameTest(templateNamespace = NS, template = TEMPLATE)
    public static void nbtManholeRegisters(GameTestHelper h) {
        h.succeedWhen(() -> {
            h.assertBlockPresent(ModRegistry.CITY_MANHOLE.get(), SAMPLE_MANHOLE);
            ManholeBlockEntity be = h.getBlockEntity(SAMPLE_MANHOLE);
            h.assertTrue(be.nodeId() != null, "no node id");
            NodeRecord r = ManholeData.get(h.getLevel().getServer()).node(be.nodeId());
            h.assertTrue(r != null, "node not in registry");
            h.assertValueEqual(r.displayName(h.getLevel().registryAccess()).getString(), "Sample Sewer", "node name");
        });
    }

    /** Place / open / close / remove through the API, stage fallback to scoreboard tags without KubeJS. */
    @GameTest(templateNamespace = NS, template = TEMPLATE)
    public static void apiPlaceOpenCloseRemove(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos rel = new BlockPos(0, 2, 2);
        BlockPos abs = h.absolutePos(rel);
        NodeRecord r = ManholesAPI.place(level, abs, "Test Node", "manholes_test:test_rule", false, null);
        h.assertTrue(r != null, "place failed");
        h.assertValueEqual(r.stageName(), "manholes_opened_test_rule", "stage name");
        @SuppressWarnings("removal")
        ServerPlayer p = player(h);
        h.assertFalse(ManholesAPI.isOpen(p, r.id), "open before prying");
        ManholesAPI.open(p, r);
        h.assertTrue(ManholesAPI.isOpen(p, r.id), "not open after open()");
        h.assertTrue(KUBEJS || p.getTags().contains("manholes_opened_test_rule"), "stage tag missing");
        h.assertTrue(level.getBlockState(abs).getValue(it.ratlab.manholes.block.ManholeBlock.OPEN), "cover not visibly open");
        ManholesAPI.close(p, r);
        h.assertFalse(ManholesAPI.isOpen(p, r.id), "still open after close()");
        h.assertFalse(!KUBEJS && p.getTags().contains("manholes_opened_test_rule"), "stage tag not removed");
        UUID id = r.id;
        h.assertTrue(ManholesAPI.remove(level, abs), "remove failed");
        h.assertTrue(ManholeData.get(level.getServer()).node(id) == null, "node still registered after removal");
        if (!KUBEJS) p.discard();
        h.succeed();
    }

    /** 1.4.0: every look is its own block, all sharing the one block entity type. */
    @GameTest(templateNamespace = NS, template = TEMPLATE)
    public static void coverBlocks(GameTestHelper h) {
        String[][] expected = {
                {"home_manhole", "home_manhole", "true"}, {"hatch", "hatch", "false"},
                {"grate", "grate", "false"}, {"cave_hole", "cave", "false"}, {"city_manhole", "city", "false"}};
        for (String[] e : expected) {
            var block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(Manholes.id(e[0]));
            h.assertTrue(block instanceof ManholeBlock, "not a cover block: manholes:" + e[0]);
            ManholeBlock m = (ManholeBlock) block;
            h.assertValueEqual(m.look(), e[1], "look of manholes:" + e[0]);
            h.assertValueEqual(m.isHome(), Boolean.parseBoolean(e[2]), "home flag of manholes:" + e[0]);
            h.assertTrue(ModRegistry.MANHOLE_BE.get().isValid(m.defaultBlockState()), "BE type rejects manholes:" + e[0]);
            h.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(Manholes.id(e[0])), "no item manholes:" + e[0]);
            h.assertTrue(m.asItem() != Items.AIR, "no block item for manholes:" + e[0]);
            h.assertValueEqual(ManholeBlock.resolve(e[1]), m, "resolve look " + e[1]);
            h.assertValueEqual(ManholeBlock.resolve("manholes:" + e[0]), m, "resolve block id manholes:" + e[0]);
            BlockPos at = new BlockPos(2, 2, 2);
            h.setBlock(at, m);
            h.assertValueEqual(((ManholeBlockEntity) h.getBlockEntity(at)).look(), e[1], "BE look on manholes:" + e[0]);
            h.assertValueEqual(m.defaultBlockState().getShape(h.getLevel(), h.absolutePos(at)).bounds().maxY, 2.0 / 16.0,
                    "hitbox height of manholes:" + e[0]);
            h.setBlock(at, Blocks.AIR);
        }
        h.assertTrue(ModRegistry.HATCH.get().defaultBlockState().is(net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE), "hatch not axe-mineable");
        h.assertTrue(ModRegistry.CAVE_HOLE.get().defaultBlockState().is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE), "cave hole not pickaxe-mineable");
        h.assertValueEqual(ModRegistry.HATCH.get().defaultBlockState().getSoundType(), net.minecraft.world.level.block.SoundType.WOOD, "hatch sound");
        h.assertValueEqual(ModRegistry.CAVE_HOLE.get().defaultBlockState().getSoundType(), net.minecraft.world.level.block.SoundType.STONE, "cave sound");
        h.assertValueEqual(ModRegistry.GRATE.get().defaultBlockState().getSoundType(), net.minecraft.world.level.block.SoundType.METAL, "grate sound");
        h.assertTrue(ManholeBlock.resolve("No Way") == null && ManholeBlock.resolve("minecraft:stone") == null
                && ManholeBlock.resolve("mypack:drain") == null, "bad ids must not resolve");
        h.succeed();
    }

    /** A 1.3.0 manholes:manhole with look NBT becomes the matching block, keeping facing, open, node id and name. */
    @GameTest(templateNamespace = NS, template = TEMPLATE)
    public static void legacyLookConversion(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos abs = h.absolutePos(SAMPLE_MANHOLE);
        level.setBlock(abs, ModRegistry.CITY_MANHOLE.get().defaultBlockState().setValue(ManholeBlock.FACING, Direction.EAST)
                .setValue(ManholeBlock.OPEN, true), 3);
        ManholeBlockEntity be = h.getBlockEntity(SAMPLE_MANHOLE);
        NodeRecord r = be.ensureRegistered();
        UUID id = r.id;
        ServerPlayer p = player(h);
        ManholeData.get(level.getServer()).open(it.ratlab.manholes.travel.Owners.ownerOf(p), id);
        be.setName("Old Sewer");
        // What an old save / structure NBT / /data merge carries.
        CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
        tag.putString("look", "cave");
        be.loadWithComponents(tag, level.registryAccess());
        h.assertTrue(be.convertLegacyLook(), "legacy look not converted");
        h.assertTrue(level.getBlockState(abs).is(ModRegistry.CAVE_HOLE.get()), "cave look must become manholes:cave_hole");
        h.assertValueEqual(level.getBlockState(abs).getValue(ManholeBlock.FACING), Direction.EAST, "facing kept");
        h.assertTrue(level.getBlockState(abs).getValue(ManholeBlock.OPEN), "open kept");
        ManholeBlockEntity now = (ManholeBlockEntity) level.getBlockEntity(abs);
        h.assertValueEqual(now.nodeId(), id, "node id kept");
        h.assertValueEqual(now.name(), "Old Sewer", "name kept");
        h.assertFalse(now.saveWithoutMetadata(level.registryAccess()).contains("look"), "look NBT is gone");
        NodeRecord after = ManholeData.get(level.getServer()).node(id);
        h.assertTrue(after != null && after.look.equals("cave"), "record follows the new block");
        h.assertTrue(ManholesAPI.network(p).stream().anyMatch(n -> n.id.equals(id)), "node left the network");
        // Scheduled path (like a chunk load): hatch through the server task queue.
        tag = now.saveWithoutMetadata(level.registryAccess());
        level.setBlock(abs, ModRegistry.CITY_MANHOLE.get().defaultBlockState(), 3);
        ManholeBlockEntity fresh = h.getBlockEntity(SAMPLE_MANHOLE);
        tag.putString("look", "manholes:hatch");
        fresh.loadWithComponents(tag, level.registryAccess());
        // A non-legacy look on a manhole is just dropped.
        BlockPos other = new BlockPos(2, 2, 2);
        h.setBlock(other, ModRegistry.CITY_MANHOLE.get());
        ManholeBlockEntity o = h.getBlockEntity(other);
        o.ensureRegistered();
        CompoundTag ot = o.saveWithoutMetadata(level.registryAccess());
        ot.putString("look", "mypack:drain");
        o.loadWithComponents(ot, level.registryAccess());
        h.assertFalse(o.convertLegacyLook(), "unknown look must not swap");
        h.succeedWhen(() -> {
            h.assertTrue(level.getBlockState(abs).is(ModRegistry.HATCH.get()), "scheduled conversion to manholes:hatch");
            h.assertValueEqual(((ManholeBlockEntity) level.getBlockEntity(abs)).nodeId(), id, "node id kept (scheduled)");
            h.assertBlockPresent(ModRegistry.CITY_MANHOLE.get(), other);
        });
    }

    /** setBlock: the command, the API, the KubeJS binding and the deprecated setLook; the JSON rule field. */
    @GameTest(templateNamespace = NS, template = TEMPLATE)
    public static void setBlock(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos abs = h.absolutePos(SAMPLE_MANHOLE);
        ManholeBlockEntity be = h.getBlockEntity(SAMPLE_MANHOLE);
        NodeRecord r = be.ensureRegistered();
        UUID id = r.id;
        h.assertValueEqual(be.look(), "city", "sample NBT manholes:manhole loads as city_manhole");

        var src = level.getServer().createCommandSourceStack().withSuppressedOutput().withLevel(level)
                .withPosition(net.minecraft.world.phys.Vec3.atCenterOf(abs));
        level.getServer().getCommands().performPrefixedCommand(src, "manholes setblock " + r.shortId() + " manholes:grate");
        h.assertTrue(level.getBlockState(abs).is(ModRegistry.GRATE.get()), "/manholes setblock <node> manholes:grate");
        level.getServer().getCommands().performPrefixedCommand(src, "manholes setblock here city");
        h.assertTrue(level.getBlockState(abs).is(ModRegistry.CITY_MANHOLE.get()), "/manholes setblock here city (look id)");
        level.getServer().getCommands().performPrefixedCommand(src, "manholes setblock here minecraft:stone");
        h.assertTrue(level.getBlockState(abs).is(ModRegistry.CITY_MANHOLE.get()), "a non-cover block must be refused");
        h.assertTrue(ManholesAPI.setBlock(level, abs, "manholes:hatch"), "API setBlock");
        h.assertTrue(level.getBlockState(abs).is(ModRegistry.HATCH.get()), "API setBlock result");
        h.assertFalse(ManholesAPI.setBlock(level, abs, "Bad Look!"), "invalid id accepted");
        h.assertFalse(ManholesAPI.setBlock(level, h.absolutePos(new BlockPos(0, 2, 0)), "hatch"), "no cover there");
        h.assertTrue(ManholesAPI.setLook(level, abs, "cave"), "deprecated setLook");
        h.assertTrue(level.getBlockState(abs).is(ModRegistry.CAVE_HOLE.get()), "setLook swaps the block");
        h.assertTrue(ManholesAPI.setLook(level, abs, ""), "setLook '' keeps the block");
        h.assertTrue(level.getBlockState(abs).is(ModRegistry.CAVE_HOLE.get()), "setLook '' is a no-op");
        if (KUBEJS) {
            var js = new it.ratlab.manholes.compat.kubejs.ManholesBindingJS();
            h.assertTrue(js.setBlock(level, abs, "manholes:city_manhole"), "Manholes.setBlock");
            h.assertTrue(level.getBlockState(abs).is(ModRegistry.CITY_MANHOLE.get()), "block from the KubeJS binding");
            h.assertTrue(js.setLook(level, abs, "grate"), "Manholes.setLook (deprecated)");
            h.assertTrue(level.getBlockState(abs).is(ModRegistry.GRATE.get()), "deprecated binding swaps the block");
        }
        ManholeBlockEntity now = (ManholeBlockEntity) level.getBlockEntity(abs);
        h.assertValueEqual(now.nodeId(), id, "node id kept across swaps");
        h.assertTrue(ManholeData.get(level.getServer()).node(id) != null, "node still registered");
        h.assertValueEqual(ManholeData.get(level.getServer()).node(id).look, now.look(), "record follows the block");
        h.assertFalse(now.isHome(), "still a world cover");

        // spawn rule JSON: "look" (look or block id) and the "block" alias
        com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString("{\"type\":\"structure\",\"look\":\"cave\"}").getAsJsonObject();
        var rule = it.ratlab.manholes.gen.SpawnRule.fromJson(ResourceLocation.parse("t:t"), o);
        h.assertValueEqual(rule.look(), "cave", "rule look");
        h.assertValueEqual(rule.block(), ModRegistry.CAVE_HOLE.get(), "rule look picks the block");
        o = com.google.gson.JsonParser.parseString("{\"type\":\"structure\",\"block\":\"manholes:city_manhole\"}").getAsJsonObject();
        h.assertValueEqual(it.ratlab.manholes.gen.SpawnRule.fromJson(ResourceLocation.parse("t:t"), o).block(),
                ModRegistry.CITY_MANHOLE.get(), "rule block alias");
        for (String bad : new String[] {"No Way", "mypack:drain", "minecraft:stone"}) {
            boolean threw = false;
            try {
                it.ratlab.manholes.gen.SpawnRule.fromJson(ResourceLocation.parse("t:t"),
                        com.google.gson.JsonParser.parseString("{\"look\":\"" + bad + "\"}").getAsJsonObject());
            } catch (com.google.gson.JsonParseException ex) {
                threw = true;
            }
            h.assertTrue(threw, "invalid rule look accepted: " + bad);
        }
        h.succeed();
    }

    /** The default recipes are loaded through the manholes:default_recipes_enabled condition, which follows the config. */
    @GameTest(templateNamespace = NS, template = TEMPLATE, batch = "recipes")
    public static void recipeToggle(GameTestHelper h) {
        var cond = it.ratlab.manholes.recipe.DefaultRecipesCondition.INSTANCE;
        h.assertTrue(net.neoforged.neoforge.registries.NeoForgeRegistries.CONDITION_SERIALIZERS
                .containsKey(Manholes.id("default_recipes_enabled")), "condition codec not registered");
        boolean enabled = ManholesConfig.b(ManholesConfig.ENABLE_DEFAULT_RECIPES);
        var rm = h.getLevel().getServer().getRecipeManager();
        h.assertValueEqual(rm.byKey(Manholes.id("crowbar")).isPresent(), enabled, "crowbar recipe loaded == enableDefaultRecipes");
        h.assertValueEqual(rm.byKey(Manholes.id("home_manhole")).isPresent(), enabled, "home manhole recipe loaded == enableDefaultRecipes");
        ManholesConfig.ENABLE_DEFAULT_RECIPES.set(false);
        boolean off = cond.test(null);
        ManholesConfig.ENABLE_DEFAULT_RECIPES.set(true);
        boolean on = cond.test(null);
        ManholesConfig.ENABLE_DEFAULT_RECIPES.set(enabled);
        h.assertFalse(off, "condition true with the toggle off");
        h.assertTrue(on, "condition false with the toggle on");
        h.succeed();
    }

    /** matchAnyCrowbar: any item whose id path contains "crowbar" pries; the tag's optional foreign crowbar (KubeJS run). */
    @GameTest(templateNamespace = NS, template = TEMPLATE, batch = "pry_any", timeoutTicks = 100)
    public static void matchAnyCrowbar(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos abs = h.absolutePos(SAMPLE_MANHOLE);
        @SuppressWarnings("removal")
        ServerPlayer p = player(h);
        ItemStack rusty = new ItemStack(ModRegistry.TEST_RUSTY_CROWBAR.get());
        h.assertFalse(rusty.is(ModRegistry.PRY_TOOLS), "test crowbar must not be in the tag");
        h.assertTrue(it.ratlab.manholes.item.PryTools.isPryTool(new ItemStack(ModRegistry.CROWBAR.get())), "our crowbar");
        h.assertFalse(it.ratlab.manholes.item.PryTools.isPryTool(new ItemStack(Items.IRON_PICKAXE)), "pickaxe");
        if (KUBEJS) {
            var zi = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse("zombie_island:crowbar"));
            h.assertTrue(zi.isPresent(), "zombie_island:crowbar not registered by the test startup script");
            h.assertTrue(new ItemStack(zi.get()).is(ModRegistry.PRY_TOOLS), "optional tag entry zombie_island:crowbar not applied");
        }
        boolean before = ManholesConfig.b(ManholesConfig.MATCH_ANY_CROWBAR);
        ManholesConfig.MATCH_ANY_CROWBAR.set(false);
        h.assertFalse(it.ratlab.manholes.item.PryTools.isPryTool(rusty), "matchAnyCrowbar=false still matches");
        p.setItemInHand(InteractionHand.MAIN_HAND, rusty.copy());
        var r1 = ManholeInteraction.useItem(p, level, abs, level.getBlockState(abs), InteractionHand.MAIN_HAND, p.getMainHandItem());
        h.assertFalse(PryHandler.isPrying(p), "prying started with matchAnyCrowbar=false (" + r1 + ")");
        ManholesConfig.MATCH_ANY_CROWBAR.set(true);
        h.assertTrue(it.ratlab.manholes.item.PryTools.isPryTool(rusty), "matchAnyCrowbar=true doesn't match *crowbar*");
        var r2 = ManholeInteraction.useItem(p, level, abs, level.getBlockState(abs), InteractionHand.MAIN_HAND, p.getMainHandItem());
        h.assertTrue(PryHandler.isPrying(p), "prying didn't start with a rusty_crowbar (" + r2 + ")");
        ManholesConfig.MATCH_ANY_CROWBAR.set(before);
        // The session times out without use packets.
        h.runAfterDelay(20, () -> {
            h.assertFalse(PryHandler.isPrying(p), "session didn't time out");
            if (!KUBEJS) p.discard();
            h.succeed();
        });
    }

    /** World manholes are unbreakable by default, home manholes are not. */
    @GameTest(templateNamespace = NS, template = TEMPLATE)
    public static void unbreakable(GameTestHelper h) {
        @SuppressWarnings("removal")
        ServerPlayer p = player(h);
        BlockPos home = new BlockPos(2, 2, 2);
        h.setBlock(home, ModRegistry.HOME_MANHOLE.get());
        float world = h.getBlockState(SAMPLE_MANHOLE).getDestroyProgress(p, h.getLevel(), h.absolutePos(SAMPLE_MANHOLE));
        float homeP = h.getBlockState(home).getDestroyProgress(p, h.getLevel(), h.absolutePos(home));
        h.assertValueEqual(world, 0.0f, "manhole destroy progress");
        h.assertTrue(homeP > 0.0f, "home manhole should be breakable");
        h.setBlock(home, Blocks.AIR);
        if (!KUBEJS) p.discard();
        h.succeed();
    }

    /**
     * World generation path: a rolled structure job places a closed, generated manhole named after the structure;
     * global spacing blocks a second one nearby; a candidate in an ungenerated chunk is queued in SavedData.
     */
    @GameTest(templateNamespace = NS, template = TEMPLATE)
    public static void generationJob(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ManholeData data = ManholeData.get(level.getServer());
        BlockPos a = h.absolutePos(new BlockPos(0, 2, 0));
        BlockPos b = h.absolutePos(new BlockPos(2, 2, 0));
        String rule = "manholes_test:test_villages";
        WorldGenHandler.runJob(level, new WorldGenHandler.Job(rule, "minecraft:village_plains", a.getY() - 3, a.getY() + 3,
                List.of(PendingPlacement.col(a.getX(), a.getZ())), Direction.NORTH));
        // The spot is the first air above the column's surface (heightmap), wherever that is in the test area.
        NodeRecord r = null;
        for (NodeRecord n : data.nodes()) {
            if (n.generated && n.dimension.equals(level.dimension()) && n.pos.getX() == a.getX() && n.pos.getZ() == a.getZ()) {
                r = n;
            }
        }
        h.assertTrue(r != null, "generated node not registered");
        h.assertTrue(level.getBlockState(r.pos).is(ModRegistry.HATCH.get()), "the rule's block (manholes:hatch) must be placed");
        h.assertFalse(level.getBlockState(r.pos).getValue(ManholeBlock.OPEN), "generated manholes start closed");
        h.assertTrue(level.getBlockState(r.pos.below()).isCollisionShapeFullBlock(level, r.pos.below()), "not on a full block");
        h.assertValueEqual(r.displayName(level.registryAccess()).getString(), "Village Plains", "name_from_structure");
        h.assertValueEqual(r.stageName(), "manholes_opened_test_villages", "stage from rule id");
        h.assertValueEqual(((ManholeBlockEntity) level.getBlockEntity(r.pos)).look(), "hatch", "look from the spawn rule");
        h.assertValueEqual(r.look, "hatch", "look in the node record");

        // Spacing: test_villages has min_distance 64, b is 2 blocks away.
        WorldGenHandler.runJob(level, new WorldGenHandler.Job(rule, "minecraft:village_plains", b.getY() - 3, b.getY() + 3,
                List.of(PendingPlacement.col(b.getX(), b.getZ())), Direction.NORTH));
        for (NodeRecord n : data.nodes()) {
            h.assertFalse(n.generated && n.pos.getX() == b.getX() && n.pos.getZ() == b.getZ(), "spacing ignored");
        }

        // A candidate in a chunk that doesn't exist yet is queued.
        int farX = a.getX() + 200_000;
        WorldGenHandler.runJob(level, new WorldGenHandler.Job(rule, "minecraft:village_plains", a.getY() - 3, a.getY() + 3,
                List.of(PendingPlacement.col(farX, a.getZ())), Direction.NORTH));
        ChunkPos farChunk = new ChunkPos(farX >> 4, a.getZ() >> 4);
        h.assertTrue(data.hasPending(level.dimension(), farChunk), "placement not queued for the ungenerated chunk");
        for (PendingPlacement p : data.pendingFor(level.dimension(), farChunk)) {
            data.removePending(p);
        }
        ManholesAPI.remove(level, r.pos);
        h.assertTrue(data.node(r.id) == null, "node not cleaned up");
        h.succeed();
    }

    /**
     * Travel: the combat blocker stops a normal request; scripted travel fades for travelFadeTicks, then lands the
     * player next to (not inside) the destination cover.
     */
    @GameTest(templateNamespace = NS, template = TEMPLATE, timeoutTicks = 400)
    public static void travelBlockerAndLanding(GameTestHelper h) {
        if (KUBEJS) {
            // KubeJS sends its own payloads on login, which the game test mock connection rejects.
            h.succeed();
            return;
        }
        ServerLevel level = h.getLevel();
        BlockPos a = h.absolutePos(new BlockPos(0, 2, 0));
        BlockPos b = h.absolutePos(new BlockPos(2, 2, 2));
        // 1.7.0: the sample manhole at (1, 2, 1) overhangs both in-template neighbours of b; remove it.
        h.setBlock(SAMPLE_MANHOLE, Blocks.AIR);
        NodeRecord from = ManholesAPI.place(level, a, "From", null, true, Direction.NORTH);
        NodeRecord to = ManholesAPI.place(level, b, "To", null, true, Direction.SOUTH);
        h.assertTrue(from != null && to != null, "place failed");
        @SuppressWarnings("removal")
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.teleportTo(level, a.getX() + 0.5, a.getY() + 0.2, a.getZ() + 0.5, 0, 0);
        ManholesAPI.open(p, from);
        ManholesAPI.open(p, to);
        p.hurt(level.damageSources().fellOutOfWorld(), 1.0f); // bypasses creative: now "in combat"
        TravelHandler.handleRequest(p, from.id, to.id);
        h.assertFalse(TravelHandler.isTravelling(p), "combat blocker ignored");
        h.assertTrue(ManholesAPI.travel(p, to), "scripted travel refused");
        h.assertTrue(TravelHandler.isTravelling(p), "no fade session");
        int fade = ManholesConfig.i(ManholesConfig.TRAVEL_FADE_TICKS);
        int total = TravelHandler.tripTicks();
        if (ManholesConfig.b(ManholesConfig.ANIMATION_ENABLED)) {
            int descent = ManholesConfig.i(ManholesConfig.DESCENT_TICKS);
            // Descent: the server put the player on the centre of the start cover (validated final position).
            h.runAfterDelay(descent / 2, () -> {
                double d = p.position().distanceTo(new net.minecraft.world.phys.Vec3(a.getX() + 0.5, a.getY() + 0.125, a.getZ() + 0.5));
                h.assertTrue(d < 0.05, "not on the start cover during the descent: " + p.position());
                h.assertFalse(p.hurt(level.damageSources().generic(), 2.0f), "damage not blocked during the descent");
            });
            // Ascent: already teleported next to the destination, still in the (invulnerable, immobile) session.
            h.runAfterDelay(descent + fade + 3, () -> {
                h.assertTrue(TravelHandler.isTravelling(p), "session ended before the ascent");
                h.assertTrue(p.position().distanceTo(b.getBottomCenter()) <= 1.6, "not teleported at the midpoint: " + p.position());
            });
        }
        h.runAfterDelay(total + 5, () -> {
            h.assertFalse(TravelHandler.isTravelling(p), "still travelling after the fade");
            double d = p.position().distanceTo(b.getBottomCenter());
            h.assertTrue(d <= 1.6, "landed too far from the destination: " + d + " at " + p.position());
            h.assertFalse(p.blockPosition().equals(b), "landed on the cover instead of next to it: " + p.position() + " cover " + b);
            h.assertTrue(level.getBlockState(p.blockPosition()).getCollisionShape(level, p.blockPosition()).isEmpty(), "landed inside a block");
            ManholesAPI.remove(level, a);
            ManholesAPI.remove(level, b);
            p.discard();
            h.succeed();
        });
    }

    @GameTest(templateNamespace = NS, template = TEMPLATE)
    public static void naming(GameTestHelper h) {
        h.assertValueEqual(Names.prettify(ResourceLocation.parse("minecraft:pillager_outpost")), "Pillager Outpost", "prettify");
        h.assertValueEqual(Names.parse("{\"text\":\"Json Name\"}", h.getLevel().registryAccess()).getString(), "Json Name", "json name");
        h.assertValueEqual(Names.parse("Plain", h.getLevel().registryAccess()).getString(), "Plain", "plain name");
        h.succeed();
    }

    // ---------------------------------------------------------------- prying (each in its own batch: they change config)

    private static void configurePhased() {
        ManholesConfig.DIFFICULTY.set(ManholesConfig.Difficulty.CUSTOM);
        ManholesConfig.INSERT_TICKS.set(3);
        ManholesConfig.SLIDE_TICKS.set(3);
        ManholesConfig.MASH_PER_PRESS.set(10.0);
        ManholesConfig.MASH_DECAY_PER_TICK.set(0.5);
        ManholesConfig.MASH_MAX_PRESSES_PER_SECOND.set(12);
        ManholesConfig.MASH_NOISE_EVERY.set(5);
        ManholesConfig.MASH_GIVE_UP_THRESHOLD.set(30.0);
        ManholesConfig.RUST_ENABLED.set(false);
    }

    private static void restorePryConfig() {
        ManholesConfig.DIFFICULTY.set(ManholesConfig.Difficulty.NORMAL);
        ManholesConfig.INSERT_TICKS.set(20);
        ManholesConfig.SLIDE_TICKS.set(30);
        ManholesConfig.MASH_PER_PRESS.set(6.0);
        ManholesConfig.MASH_DECAY_PER_TICK.set(0.8);
        ManholesConfig.MASH_MAX_PRESSES_PER_SECOND.set(12);
        ManholesConfig.MASH_NOISE_EVERY.set(5);
        ManholesConfig.MASH_GIVE_UP_THRESHOLD.set(30.0);
        ManholesConfig.RUST_ENABLED.set(true);
    }

    private static ItemStack giveCrowbar(ServerPlayer p) {
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.CROWBAR.get()));
        return p.getItemInHand(InteractionHand.MAIN_HAND);
    }

    /** One simulated use packet (what the client sends every 4 ticks while right-click is held). */
    private static void use(ServerPlayer p, ServerLevel level, BlockPos abs) {
        ManholeInteraction.useItem(p, level, abs, level.getBlockState(abs), InteractionHand.MAIN_HAND, p.getMainHandItem());
    }

    /** The crowbar item, the pry_tools tag (crowbar only) and simple mode (hold pryTicks, the 1.0 behaviour). */
    @GameTest(templateNamespace = NS, template = TEMPLATE, batch = "pry_simple", timeoutTicks = 200)
    public static void prySimple(GameTestHelper h) {
        ItemStack crowbarStack = new ItemStack(ModRegistry.CROWBAR.get());
        h.assertTrue(crowbarStack.is(ModRegistry.PRY_TOOLS), "crowbar not in #manholes:pry_tools");
        h.assertFalse(new ItemStack(Items.IRON_PICKAXE).is(ModRegistry.PRY_TOOLS), "pickaxes must not be pry tools any more");
        h.assertValueEqual(crowbarStack.getMaxStackSize(), 1, "crowbar stack size");
        h.assertValueEqual(crowbarStack.getMaxDamage(), ManholesStartupConfig.crowbarDurability(), "crowbar durability");
        h.assertTrue(crowbarStack.getItem().isValidRepairItem(crowbarStack, new ItemStack(Items.IRON_INGOT)), "iron ingot doesn't repair");
        h.assertFalse(crowbarStack.getItem().isValidRepairItem(crowbarStack, new ItemStack(Items.GOLD_INGOT)), "gold ingot repairs");

        ManholesConfig.DIFFICULTY.set(ManholesConfig.Difficulty.SIMPLE);
        ManholesConfig.PRY_TICKS.set(20);
        ServerLevel level = h.getLevel();
        BlockPos abs = h.absolutePos(new BlockPos(0, 2, 2));
        NodeRecord r = ManholesAPI.place(level, abs, "Simple", "manholes_test:pry_simple", false, null);
        h.assertTrue(r != null, "place failed");
        ServerPlayer p = player(h);
        ItemStack tool = giveCrowbar(p);
        int[] t = {0};
        boolean[] sawSimple = {false};
        h.onEachTick(() -> {
            if (!ManholesAPI.isOpen(p, r.id) && t[0]++ % 4 == 0) {
                use(p, level, abs);
            }
            PryHandler.Snapshot snap = PryHandler.snapshot(p);
            if (snap != null && snap.phase().equals("simple")) {
                sawSimple[0] = true;
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(ManholesAPI.isOpen(p, r.id), "not pried open yet");
            h.assertTrue(sawSimple[0], "no simple session");
            h.assertTrue(p.hasInfiniteMaterials() || tool.getDamageValue() == 1, "crowbar not damaged by 1: " + tool.getDamageValue());
            ManholesAPI.remove(level, abs);
            restorePryConfig();
            ManholesConfig.PRY_TICKS.set(60);
            if (!KUBEJS) p.discard();
        });
    }

    /**
     * Mashing, success: simulated use packets every 4 ticks and a mash press every 2 ticks (10/s, under the cap) go
     * through insert, lever and slide, then the node opens.
     */
    @GameTest(templateNamespace = NS, template = TEMPLATE, batch = "pry_mash_ok", timeoutTicks = 400)
    public static void mashSuccess(GameTestHelper h) {
        configurePhased();
        ServerLevel level = h.getLevel();
        BlockPos abs = h.absolutePos(new BlockPos(0, 2, 2));
        NodeRecord r = ManholesAPI.place(level, abs, "Phased", "manholes_test:pry_phased", false, null);
        h.assertTrue(r != null, "place failed");
        ServerPlayer p = player(h);
        giveCrowbar(p);
        int[] t = {0};
        java.util.Set<String> phases = new java.util.HashSet<>();
        int[] presses = {0};
        boolean[] earlyPress = {false};
        h.onEachTick(() -> {
            if (ManholesAPI.isOpen(p, r.id)) {
                return;
            }
            int tick = t[0]++;
            if (tick % 4 == 0) {
                use(p, level, abs);
            }
            PryHandler.Snapshot snap = PryHandler.snapshot(p);
            if (snap == null) {
                return;
            }
            phases.add(snap.phase());
            presses[0] = Math.max(presses[0], snap.presses());
            if (snap.phase().equals("insert") && PryHandler.handleMash(p)) {
                earlyPress[0] = true; // presses outside LEVER must be ignored
            }
            if (snap.phase().equals("lever") && tick % 2 == 0) {
                PryHandler.handleMash(p);
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(ManholesAPI.isOpen(p, r.id), "not pried open yet, phases " + phases + ", presses " + presses[0]);
            h.assertTrue(phases.containsAll(List.of("insert", "lever", "slide")), "phases seen: " + phases);
            h.assertFalse(earlyPress[0], "a press during INSERT was accepted");
            h.assertTrue(presses[0] >= 10, "presses: " + presses[0]);
            if (KUBEJS) {
                // the example script tags the player in ManholeEvents.pryPhase / mash; skillCheck (deprecated) never fires
                h.assertTrue(p.getTags().contains("manholes_example_phase_slide"), "pryPhase event not seen: " + p.getTags());
                h.assertTrue(p.getTags().contains("manholes_example_mash"), "mash event not seen: " + p.getTags());
                h.assertFalse(p.getTags().contains("manholes_example_skill_never"), "deprecated skillCheck fired");
            }
            ManholesAPI.remove(level, abs);
            restorePryConfig();
            if (!KUBEJS) p.discard();
        });
    }

    /** Decay: without presses the bar loses mashDecayPerTick per tick and stops at 0 (give-up disabled here). */
    @GameTest(templateNamespace = NS, template = TEMPLATE, batch = "pry_mash_decay", timeoutTicks = 300)
    public static void mashDecay(GameTestHelper h) {
        configurePhased();
        ManholesConfig.MASH_DECAY_PER_TICK.set(5.0);
        ManholesConfig.MASH_GIVE_UP_THRESHOLD.set(100.0); // never gives up
        ServerLevel level = h.getLevel();
        BlockPos abs = h.absolutePos(new BlockPos(0, 2, 2));
        NodeRecord r = ManholesAPI.place(level, abs, "Decay", "manholes_test:pry_decay", false, null);
        h.assertTrue(r != null, "place failed");
        ServerPlayer p = player(h);
        giveCrowbar(p);
        int[] t = {0};
        int[] leverTick = {-1};
        float[] after3 = {-1f};
        boolean[] done = {false};
        h.onEachTick(() -> {
            if (done[0]) {
                return;
            }
            int tick = t[0]++;
            if (tick % 4 == 0) {
                use(p, level, abs);
            }
            PryHandler.Snapshot snap = PryHandler.snapshot(p);
            if (snap == null || !snap.phase().equals("lever")) {
                return;
            }
            if (leverTick[0] < 0) {
                leverTick[0] = tick;
                for (int i = 0; i < 4; i++) {
                    PryHandler.handleMash(p); // 40 %
                }
                h.assertTrue(Math.abs(PryHandler.snapshot(p).lever() - 0.4f) < 0.001f, "4 presses != 40%: " + PryHandler.snapshot(p).lever());
            } else if (tick == leverTick[0] + 4) {
                after3[0] = snap.lever(); // 3 ticks without a press since the pressed tick
            } else if (tick == leverTick[0] + 20) {
                h.assertTrue(after3[0] > 0.1f && after3[0] < 0.4f, "no decay: " + after3[0]);
                h.assertTrue(Math.abs(after3[0] - 0.25f) < 0.06f, "decay per tick not 5%: " + after3[0]);
                h.assertValueEqual(snap.lever(), 0f, "bar below 0 or not decayed");
                h.assertValueEqual(snap.phase(), "lever", "session not in lever");
                done[0] = true;
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(done[0], "decay not measured");
            h.assertFalse(ManholesAPI.isOpen(p, r.id), "opened without mashing");
            ManholesAPI.remove(level, abs);
            restorePryConfig();
            if (!KUBEJS) p.discard();
        });
    }

    /** Anti-autoclicker: at most mashMaxPressesPerSecond presses count in any 20-tick window. */
    @GameTest(templateNamespace = NS, template = TEMPLATE, batch = "pry_mash_rate", timeoutTicks = 300)
    public static void mashRateCap(GameTestHelper h) {
        configurePhased();
        ManholesConfig.MASH_PER_PRESS.set(1.0);
        ManholesConfig.MASH_DECAY_PER_TICK.set(0.0);
        ServerLevel level = h.getLevel();
        BlockPos abs = h.absolutePos(new BlockPos(0, 2, 2));
        NodeRecord r = ManholesAPI.place(level, abs, "Rate", "manholes_test:pry_rate", false, null);
        h.assertTrue(r != null, "place failed");
        ServerPlayer p = player(h);
        giveCrowbar(p);
        int[] t = {0};
        int[] leverTick = {-1};
        boolean[] done = {false};
        h.onEachTick(() -> {
            if (done[0]) {
                return;
            }
            int tick = t[0]++;
            if (tick % 4 == 0) {
                use(p, level, abs);
            }
            PryHandler.Snapshot snap = PryHandler.snapshot(p);
            if (snap == null || !snap.phase().equals("lever")) {
                return;
            }
            if (leverTick[0] < 0) {
                leverTick[0] = tick;
                int ok = 0;
                for (int i = 0; i < 20; i++) {
                    ok += PryHandler.handleMash(p) ? 1 : 0;
                }
                h.assertValueEqual(ok, 12, "presses accepted in one burst");
                h.assertValueEqual(PryHandler.snapshot(p).rejected(), 8, "presses rejected");
            } else if (tick == leverTick[0] + 10) {
                h.assertFalse(PryHandler.handleMash(p), "press accepted inside the full window");
            } else if (tick == leverTick[0] + 21) {
                h.assertTrue(PryHandler.handleMash(p), "press refused after the window passed");
                h.assertValueEqual(PryHandler.snapshot(p).presses(), 13, "accepted presses");
                done[0] = true;
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(done[0], "rate cap not measured");
            ManholesAPI.remove(level, abs);
            restorePryConfig();
            if (!KUBEJS) p.discard();
        });
    }

    /** Give-up rule: after reaching mashGiveUpThreshold, a bar that decays to 0 ends with "the crowbar slips". */
    @GameTest(templateNamespace = NS, template = TEMPLATE, batch = "pry_mash_giveup", timeoutTicks = 300)
    public static void mashGiveUp(GameTestHelper h) {
        configurePhased();
        ManholesConfig.MASH_DECAY_PER_TICK.set(5.0);
        ServerLevel level = h.getLevel();
        BlockPos abs = h.absolutePos(new BlockPos(0, 2, 2));
        NodeRecord r = ManholesAPI.place(level, abs, "Stuck", "manholes_test:pry_stuck", false, null);
        h.assertTrue(r != null, "place failed");
        ServerPlayer p = player(h);
        ItemStack tool = giveCrowbar(p);
        int[] t = {0};
        boolean[] pressed = {false};
        boolean[] started = {false};
        h.onEachTick(() -> {
            if (t[0]++ % 4 == 0) {
                use(p, level, abs); // keeps "holding" after the slip: must not restart
            }
            PryHandler.Snapshot snap = PryHandler.snapshot(p);
            if (snap == null) {
                return;
            }
            started[0] = true;
            if (snap.phase().equals("lever") && !pressed[0]) {
                pressed[0] = true;
                for (int i = 0; i < 2; i++) {
                    PryHandler.handleMash(p); // 20 %: below the threshold, decaying to 0 must NOT slip yet
                }
            }
            if (snap.phase().equals("lever") && pressed[0] && snap.lever() == 0f && snap.peak() < 0.3f) {
                for (int i = 0; i < 4; i++) {
                    PryHandler.handleMash(p); // 40 %: above the threshold now
                }
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(started[0], "no session");
            h.assertTrue(PryHandler.snapshot(p) == null, "session still running");
            h.assertTrue(PryHandler.mustRelease(p), "slip doesn't require releasing the key");
            h.assertFalse(ManholesAPI.isOpen(p, r.id), "opened despite slipping");
            int cost = ManholesConfig.i(ManholesConfig.FAIL_DURABILITY_COST);
            h.assertTrue(p.hasInfiniteMaterials() || tool.getDamageValue() == cost, "slip durability cost: " + tool.getDamageValue());
            ManholesAPI.remove(level, abs);
            restorePryConfig();
            if (!KUBEJS) p.discard();
        });
    }

    /** Rust is derived deterministically from the node id, stored in the block entity, NBT-overridable, 0 for homes. */
    @GameTest(templateNamespace = NS, template = TEMPLATE, batch = "pry_rust")
    public static void rustDeterministic(GameTestHelper h) {
        UUID fixed = UUID.fromString("1b2c3d4e-0000-4000-8000-00000000abcd");
        int a = ManholeBlockEntity.rustFor(fixed);
        h.assertValueEqual(ManholeBlockEntity.rustFor(UUID.fromString(fixed.toString())), a, "same uuid, same rust");
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        java.util.Random rnd = new java.util.Random(42);
        for (int i = 0; i < 400; i++) {
            int v = ManholeBlockEntity.rustFor(new UUID(rnd.nextLong(), rnd.nextLong()));
            h.assertTrue(v >= 0 && v <= 3, "rust out of range: " + v);
            seen.add(v);
        }
        h.assertValueEqual(seen.size(), 4, "rust levels used");

        ServerLevel level = h.getLevel();
        BlockPos abs = h.absolutePos(new BlockPos(0, 2, 2));
        NodeRecord r = ManholesAPI.place(level, abs, "Rusty", null, false, null);
        h.assertTrue(r != null, "place failed");
        ManholeBlockEntity be = (ManholeBlockEntity) level.getBlockEntity(abs);
        h.assertValueEqual(be.rust(), ManholeBlockEntity.rustFor(r.id), "block entity rust = rustFor(node id)");
        CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
        h.assertValueEqual(tag.getInt("rust"), be.rust(), "rust saved in NBT");
        int other = (be.rust() + 1) % 4;
        tag.putInt("rust", other);
        be.loadCustomOnly(tag, level.registryAccess());
        h.assertValueEqual(be.rust(), other, "NBT override");
        h.assertValueEqual(PryHandler.rustAt(level, abs), other, "rustAt");

        ManholesConfig.DIFFICULTY.set(ManholesConfig.Difficulty.NORMAL);
        ManholesConfig.RUST_ENABLED.set(true);
        PryParams p2 = PryParams.resolve(2);
        h.assertValueEqual(p2.insertTicks(), 30, "normal insert with rust 2 (20 * 1.5)");
        h.assertValueEqual(p2.slideTicks(), 45, "normal slide with rust 2 (30 * 1.5)");
        h.assertTrue(Math.abs(p2.perPress() - 0.04f) < 1e-5f, "normal mashPerPress with rust 2 (6 / 1.5): " + p2.perPress());
        h.assertTrue(Math.abs(p2.decay() - 0.008f) < 1e-5f, "normal decay: " + p2.decay());
        ManholesConfig.DIFFICULTY.set(ManholesConfig.Difficulty.HARD);
        PryParams hard = PryParams.resolve(0);
        h.assertTrue(Math.abs(hard.perPress() - 0.045f) < 1e-5f && Math.abs(hard.decay() - 0.01f) < 1e-5f,
                "hard preset: " + hard.perPress() + " / " + hard.decay());
        ManholesConfig.DIFFICULTY.set(ManholesConfig.Difficulty.NORMAL);
        ManholesAPI.remove(level, abs);

        BlockPos home = new BlockPos(2, 2, 2);
        h.setBlock(home, ModRegistry.HOME_MANHOLE.get());
        ManholeBlockEntity hbe = h.getBlockEntity(home);
        h.assertValueEqual(hbe.rust(), 0, "home manholes have no rust");
        h.setBlock(home, Blocks.AIR);
        h.succeed();
    }

    // ---------------------------------------------------------------- aliases, landing

    /** A per-owner alias wins over the node's own name for that owner only; "" clears it; renames need membership. */
    @GameTest(templateNamespace = NS, template = TEMPLATE)
    public static void aliasPriority(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos abs = h.absolutePos(new BlockPos(0, 2, 2));
        NodeRecord r = ManholesAPI.place(level, abs, "Base Name", null, false, null);
        h.assertTrue(r != null, "place failed");
        ServerPlayer p = player(h);
        ManholeData data = ManholeData.get(level.getServer());
        UUID owner = it.ratlab.manholes.travel.Owners.ownerOf(p);
        UUID stranger = UUID.fromString("00000000-0000-4000-8000-0000000000aa");
        var regs = level.registryAccess();

        TravelHandler.handleRename(p, r.id, "Not Yet"); // not in the network: refused
        h.assertValueEqual(data.alias(owner, r.id), "", "alias set for a node outside the network");
        ManholesAPI.open(p, r);
        TravelHandler.handleRename(p, r.id, "  Our Sewer  ");
        h.assertValueEqual(data.displayName(owner, r, regs).getString(), "Our Sewer", "alias not used for its owner");
        h.assertValueEqual(data.displayName(stranger, r, regs).getString(), "Base Name", "alias leaked to another owner");
        h.assertValueEqual(r.displayName(regs).getString(), "Base Name", "base name overwritten");
        var entry = TravelHandler.entry(p, r);
        h.assertValueEqual(entry.name().getString(), "Our Sewer", "entry name");
        h.assertTrue(entry.aliased(), "entry not flagged as alias");
        h.assertTrue(ManholesAPI.rename(level.getServer(), r, "Renamed Base"), "base rename failed");
        h.assertValueEqual(data.displayName(owner, r, regs).getString(), "Our Sewer", "base rename beat the alias");
        h.assertValueEqual(TravelHandler.sanitizeAlias("x".repeat(40)).length(), 32, "alias length cap");
        h.assertValueEqual(TravelHandler.sanitizeAlias("§cRed"), "cRed", "formatting codes kept");
        TravelHandler.handleRename(p, r.id, "");
        h.assertValueEqual(data.alias(owner, r.id), "", "empty name did not clear the alias");
        h.assertValueEqual(data.displayName(owner, r, regs).getString(), "Renamed Base", "cleared alias still shown");
        ManholesAPI.setAlias(p, r, "Again");
        UUID id = r.id;
        ManholesAPI.remove(level, abs);
        h.assertValueEqual(data.alias(owner, id), "", "alias kept after the node was removed");
        if (!KUBEJS) p.discard();
        h.succeed();
    }

    /** Landing spots need two full free blocks above the standing surface, also with a slab at the feet. */
    @GameTest(templateNamespace = NS, template = TEMPLATE)
    public static void landingHeadroom(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos feet = h.absolutePos(new BlockPos(2, 2, 0));
        level.setBlockAndUpdate(feet.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(feet, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(feet.above(), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(feet.above(2), Blocks.AIR.defaultBlockState());
        h.assertTrue(TravelHandler.standable(level, feet), "open floor not standable");
        level.setBlockAndUpdate(feet.above(), Blocks.STONE.defaultBlockState());
        h.assertFalse(TravelHandler.standable(level, feet), "standable with a block at head height");
        level.setBlockAndUpdate(feet.above(), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(feet, Blocks.STONE_SLAB.defaultBlockState());
        h.assertTrue(TravelHandler.standable(level, feet), "bottom slab with 2 free blocks not standable");
        level.setBlockAndUpdate(feet.above(2), Blocks.STONE.defaultBlockState());
        h.assertFalse(TravelHandler.standable(level, feet), "slab at the feet with a block 2 above: head would clip");
        level.setBlockAndUpdate(feet.above(2), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(feet, Blocks.AIR.defaultBlockState());
        h.succeed();
    }

    // ---------------------------------------------------------------- 1.5.0

    private static ServerPlayer fake(GameTestHelper h, String name, String uuid) {
        return net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.fromString(uuid), name));
    }

    /** A placed home manhole is its owner's only; sharing without FTB Teams = owner only; only the owner toggles it. */
    @GameTest(templateNamespace = NS, template = TEMPLATE, batch = "home")
    public static void homePrivateShared(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        var server = level.getServer();
        ServerPlayer alice = fake(h, "Alice", "a11ce000-0000-4000-8000-000000000001");
        ServerPlayer bob = fake(h, "Bob", "b0b00000-0000-4000-8000-000000000002");
        BlockPos abs = h.absolutePos(new BlockPos(2, 2, 2));
        var state = ModRegistry.HOME_MANHOLE.get().defaultBlockState();
        level.setBlock(abs, state, 3);
        state.getBlock().setPlacedBy(level, abs, state, alice, new ItemStack(ModRegistry.HOME_MANHOLE_ITEM.get()));
        ManholeBlockEntity be = (ManholeBlockEntity) level.getBlockEntity(abs);
        NodeRecord r = ManholeData.get(server).node(be.nodeId());
        h.assertTrue(r != null && r.home, "home node not registered");
        h.assertValueEqual(r.owner, alice.getUUID(), "owner = placer (node)");
        h.assertValueEqual(be.owner(), alice.getUUID(), "owner = placer (block entity)");
        h.assertValueEqual(r.ownerName, "Alice", "owner name");
        h.assertFalse(r.shared, "private by default");
        h.assertFalse(ManholeData.get(server).isOpen(it.ratlab.manholes.travel.Owners.ownerOf(alice), r.id),
                "a home must not join the team network");
        h.assertTrue(Access.canSee(alice, r), "owner can't see his home");
        h.assertFalse(Access.canSee(bob, r), "private home visible to another player");
        h.assertTrue(ManholesAPI.network(alice).contains(r), "home missing from the owner's list");
        h.assertFalse(ManholesAPI.network(bob).contains(r), "private home in another player's list");
        var entry = TravelHandler.entry(alice, r);
        h.assertTrue(entry.home() && entry.mine() && !entry.shared() && entry.ownerName().equals("Alice"), "entry: " + entry);

        // Owner-only toggle: Bob (not op) is refused, Alice may.
        h.assertFalse(HomeManholes.setShared(server, bob, r, true), "a non-owner toggled sharing");
        h.assertFalse(r.shared, "sharing changed by a non-owner");
        h.assertTrue(HomeManholes.setShared(server, alice, r, true), "owner toggle refused");
        h.assertTrue(r.shared && be.shared(), "shared not stored on node + block entity");
        h.assertTrue(TravelHandler.entry(alice, r).shared(), "entry not shared");
        // Without FTB Teams there are no team mates: shared = owner only.
        h.assertFalse(Access.canSee(bob, r), "shared home visible without a common team");
        // Sneak + right-click by the owner toggles; by someone else does nothing.
        alice.setShiftKeyDown(true);
        ManholeInteraction.useEmpty(alice, level, abs, level.getBlockState(abs));
        alice.setShiftKeyDown(false);
        h.assertFalse(r.shared, "sneak-use by the owner didn't toggle");
        bob.setShiftKeyDown(true);
        ManholeInteraction.useEmpty(bob, level, abs, level.getBlockState(abs));
        bob.setShiftKeyDown(false);
        h.assertFalse(r.shared, "sneak-use by a non-owner toggled");
        // The command: the console may, the KubeJS binding / API is trusted.
        var src = server.createCommandSourceStack().withSuppressedOutput().withLevel(level);
        server.getCommands().performPrefixedCommand(src, "manholes share " + r.shortId() + " true");
        h.assertTrue(r.shared, "/manholes share <node> true");
        h.assertTrue(ManholesAPI.setShared(level, abs, false), "API setShared");
        h.assertFalse(r.shared, "API setShared false");
        if (KUBEJS) {
            h.assertTrue(new it.ratlab.manholes.compat.kubejs.ManholesBindingJS().setShared(level, abs, true), "Manholes.setShared");
            h.assertTrue(r.shared, "Manholes.setShared true");
            ManholesAPI.setShared(level, abs, false);
        }
        // Base name: only the owner (name tag).
        ItemStack tag = new ItemStack(Items.NAME_TAG);
        tag.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Bob's Now"));
        bob.setItemInHand(InteractionHand.MAIN_HAND, tag);
        ManholeInteraction.useItem(bob, level, abs, level.getBlockState(abs), InteractionHand.MAIN_HAND, bob.getMainHandItem());
        h.assertValueEqual(be.name(), "", "a non-owner renamed the home");
        ItemStack tag2 = tag.copy();
        tag2.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Alice Home"));
        alice.setItemInHand(InteractionHand.MAIN_HAND, tag2);
        ManholeInteraction.useItem(alice, level, abs, level.getBlockState(abs), InteractionHand.MAIN_HAND, alice.getMainHandItem());
        h.assertValueEqual(r.displayName(level.registryAccess()).getString(), "Alice Home", "owner rename");
        // Breaking: only the owner in survival, unless home.anyoneCanBreak.
        h.assertTrue(ManholeBlock.mayBreakHome(alice, level, abs), "owner can't break");
        h.assertFalse(ManholeBlock.mayBreakHome(bob, level, abs), "non-owner can break");
        h.assertValueEqual(level.getBlockState(abs).getDestroyProgress(bob, level, abs), 0.0f, "non-owner destroy progress");
        boolean before = ManholesConfig.b(ManholesConfig.HOME_ANYONE_CAN_BREAK);
        ManholesConfig.HOME_ANYONE_CAN_BREAK.set(true);
        boolean anyone = ManholeBlock.mayBreakHome(bob, level, abs);
        ManholesConfig.HOME_ANYONE_CAN_BREAK.set(before);
        h.assertTrue(anyone, "anyoneCanBreak ignored");
        UUID id = r.id;
        level.setBlock(abs, Blocks.AIR.defaultBlockState(), 3);
        h.assertTrue(ManholeData.get(server).node(id) == null, "home node kept after removal");
        h.succeed();
    }

    /** Pre-1.5.0 homes: owner from the network that held it; otherwise unowned + shared, claimed by the first user. */
    @GameTest(templateNamespace = NS, template = TEMPLATE, batch = "home")
    public static void homeMigration(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        var server = level.getServer();
        ManholeData data = ManholeData.get(server);
        // Carol must be resolvable as a player: online (mock player in the player list) or in the profile cache.
        @SuppressWarnings("removal")
        ServerPlayer carol = KUBEJS ? fake(h, "Carol", "ca401000-0000-4000-8000-000000000003") : h.makeMockServerPlayerInLevel();
        boolean resolvable = !KUBEJS || server.getProfileCache() != null;
        ServerPlayer dave = fake(h, "Dave", "da7e0000-0000-4000-8000-000000000004");
        if (KUBEJS && server.getProfileCache() != null) {
            server.getProfileCache().add(carol.getGameProfile());
        }
        // An old home: no owner, in Carol's network (1.4.0 placement opened it for the placer).
        BlockPos a = new BlockPos(2, 2, 2);
        h.setBlock(a, ModRegistry.HOME_MANHOLE.get());
        ManholeBlockEntity be = h.getBlockEntity(a);
        NodeRecord r = be.ensureRegistered();
        h.assertTrue(r.owner == null && Access.isShared(r), "an unowned home counts as shared");
        data.open(it.ratlab.manholes.travel.Owners.ownerOf(carol), r.id);
        // A second one nobody opened.
        BlockPos b = new BlockPos(0, 2, 2);
        h.setBlock(b, ModRegistry.HOME_MANHOLE.get());
        NodeRecord r2 = ((ManholeBlockEntity) h.getBlockEntity(b)).ensureRegistered();
        data.migrateLegacyHomes(server);
        if (resolvable) {
            h.assertValueEqual(r.owner, carol.getUUID(), "owner from the old network");
            h.assertValueEqual(r.ownerName, carol.getGameProfile().getName(), "owner name");
            h.assertFalse(r.shared, "an attributed home becomes private");
            be.ensureRegistered();
            h.assertValueEqual(be.owner(), carol.getUUID(), "block entity adopts the migrated owner");
            h.assertFalse(Access.canSee(dave, r), "migrated private home visible to someone else");
        }
        h.assertTrue(r2.owner == null && r2.shared, "an unattributable home stays unowned and shared");
        // First use claims it.
        ManholeInteraction.useEmpty(dave, level, h.absolutePos(b), level.getBlockState(h.absolutePos(b)));
        h.assertValueEqual(r2.owner, dave.getUUID(), "first user claims an unowned home");
        h.assertTrue(r2.shared, "a claimed home keeps being shared");
        h.assertTrue(Access.canSee(dave, r2), "claimer can't see it");
        h.setBlock(a, Blocks.AIR);
        h.setBlock(b, Blocks.AIR);
        if (!KUBEJS) carol.discard();
        h.succeed();
    }

    /** network_sync carries each node's look (the FTB Chunks / travel-screen icon is picked from it), also on the wire. */
    @GameTest(templateNamespace = NS, template = TEMPLATE, batch = "home")
    public static void syncCarriesLook(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = fake(h, "Erin", "e4190000-0000-4000-8000-000000000006");
        Object[][] covers = {{ModRegistry.HATCH.get(), "hatch"}, {ModRegistry.GRATE.get(), "grate"},
                {ModRegistry.CAVE_HOLE.get(), "cave"}, {ModRegistry.CITY_MANHOLE.get(), "city"}, {ModRegistry.HOME_MANHOLE.get(), "home_manhole"}};
        java.util.Map<UUID, String> expected = new java.util.HashMap<>();
        java.util.List<BlockPos> placed = new java.util.ArrayList<>();
        for (int i = 0; i < covers.length; i++) {
            BlockPos rel = new BlockPos(i % 3, 2, i < 3 ? 0 : 2);
            if (rel.equals(SAMPLE_MANHOLE)) {
                rel = new BlockPos(2, 2, 1);
            }
            BlockPos abs = h.absolutePos(rel);
            ManholeBlock b = (ManholeBlock) covers[i][0];
            level.setBlock(abs, b.defaultBlockState(), 3);
            if (b.isHome()) {
                b.setPlacedBy(level, abs, b.defaultBlockState(), p, new ItemStack(b));
            }
            NodeRecord r = ((ManholeBlockEntity) level.getBlockEntity(abs)).ensureRegistered();
            if (!b.isHome()) {
                ManholeData.get(level.getServer()).open(it.ratlab.manholes.travel.Owners.ownerOf(p), r.id);
            }
            expected.put(r.id, (String) covers[i][1]);
            placed.add(abs);
        }
        var payload = it.ratlab.manholes.travel.NetworkSync.payload(p);
        // Round trip through the real stream codec.
        var buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), level.registryAccess());
        it.ratlab.manholes.net.NetworkSyncPayload.CODEC.encode(buf, payload);
        var decoded = it.ratlab.manholes.net.NetworkSyncPayload.CODEC.decode(buf);
        buf.release();
        java.util.Map<UUID, String> got = new java.util.HashMap<>();
        for (var e : decoded.nodes()) {
            got.put(e.id(), e.look());
        }
        for (var ex : expected.entrySet()) {
            h.assertValueEqual(got.get(ex.getKey()), ex.getValue(), "look in network_sync for " + ex.getValue());
        }
        for (BlockPos abs : placed) {
            level.setBlock(abs, Blocks.AIR.defaultBlockState(), 3);
        }
        h.succeed();
    }

    /** manholes:manhole is an alias of manholes:city_manhole: registry, block state NBT, block entity data. */
    @GameTest(templateNamespace = NS, template = TEMPLATE)
    public static void manholeAlias(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        var bl = net.minecraft.core.registries.BuiltInRegistries.BLOCK;
        h.assertValueEqual(bl.get(Manholes.id("manhole")), ModRegistry.CITY_MANHOLE.get(), "block alias");
        h.assertValueEqual(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(Manholes.id("manhole")),
                ModRegistry.CITY_MANHOLE_ITEM.get(), "item alias");
        h.assertValueEqual(bl.getKey(ModRegistry.CITY_MANHOLE.get()), Manholes.id("city_manhole"), "the real id");
        // A chunk / structure palette entry of 1.4.0.
        CompoundTag st = new CompoundTag();
        st.putString("Name", "manholes:manhole");
        CompoundTag props = new CompoundTag();
        props.putString("facing", "east");
        props.putString("open", "true");
        st.put("Properties", props);
        var state = net.minecraft.nbt.NbtUtils.readBlockState(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), st);
        h.assertTrue(state.is(ModRegistry.CITY_MANHOLE.get()), "palette manholes:manhole -> " + state);
        h.assertValueEqual(state.getValue(ManholeBlock.FACING), Direction.EAST, "facing kept");
        h.assertTrue(state.getValue(ManholeBlock.OPEN), "open kept");
        // Its block entity data (same BE type id manholes:manhole).
        UUID node = UUID.fromString("0dd0dd00-0000-4000-8000-000000000005");
        CompoundTag bet = new CompoundTag();
        bet.putString("id", "manholes:manhole");
        bet.putUUID("node_id", node);
        bet.putString("name", "Old Drain");
        bet.putInt("rust", 2);
        var loaded = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(h.absolutePos(new BlockPos(2, 2, 2)), state, bet,
                level.registryAccess());
        h.assertTrue(loaded instanceof ManholeBlockEntity, "block entity not loaded: " + loaded);
        ManholeBlockEntity mbe = (ManholeBlockEntity) loaded;
        h.assertValueEqual(mbe.nodeId(), node, "node id kept");
        h.assertValueEqual(mbe.name(), "Old Drain", "name kept");
        h.assertValueEqual(mbe.rust(), 2, "rust kept");
        h.assertValueEqual(mbe.look(), "city", "look of the migrated block");
        h.succeed();
    }

    /** Structure rules: candidates on a ring minOffset..maxOffset outside the box, never inside any structure. */
    @GameTest(templateNamespace = NS, template = TEMPLATE, batch = "ring")
    public static void ringPlacement(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        var rule = it.ratlab.manholes.gen.SpawnRule.fromJson(ResourceLocation.parse("t:ring"), com.google.gson.JsonParser
                .parseString("{\"type\":\"structure\",\"offset\":[6,24],\"placement\":{\"margin\":2}}").getAsJsonObject());
        h.assertTrue(rule.minOffset() == 6 && rule.maxOffset() == 24 && rule.margin() == 2, "offset / margin from JSON");
        h.assertTrue(new it.ratlab.manholes.gen.SpawnRule(ResourceLocation.parse("t:d"), it.ratlab.manholes.gen.SpawnRule.Type.STRUCTURE)
                .minOffset() == 6, "default offset");
        h.assertTrue(rule.offset(30, 10).minOffset() == 10 && rule.maxOffset() == 30, "builder offset(min, max) sorts");
        rule.offset(6, 24);
        net.minecraft.world.level.levelgen.structure.BoundingBox box =
                new net.minecraft.world.level.levelgen.structure.BoundingBox(100, 60, 200, 140, 80, 230);
        var j1 = WorldGenHandler.structureJob(rule, ResourceLocation.parse("minecraft:village_plains"), box,
                net.minecraft.util.RandomSource.create(1234L));
        var j2 = WorldGenHandler.structureJob(rule, ResourceLocation.parse("minecraft:village_plains"), box,
                net.minecraft.util.RandomSource.create(1234L));
        h.assertValueEqual(j1.columns(), j2.columns(), "the roll is deterministic");
        h.assertTrue(j1.columns().size() == ManholesConfig.i(ManholesConfig.PLACEMENT_ATTEMPTS), "candidate count");
        java.util.Set<Integer> dists = new java.util.HashSet<>();
        for (long c : j1.columns()) {
            int d = WorldGenHandler.ringDistance(box, PendingPlacement.colX(c), PendingPlacement.colZ(c));
            h.assertTrue(d >= 6 && d <= 24, "candidate " + d + " blocks from the box");
            dists.add(d);
        }
        h.assertTrue(dists.size() > 3, "offsets not spread: " + dists);

        // A structure start registered on this chunk: spots inside its box (or within margin) are refused.
        BlockPos in = h.absolutePos(new BlockPos(1, 2, 1));
        var chunk = level.getChunkAt(in);
        var reg = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
        var structure = reg.get(ResourceLocation.parse("minecraft:igloo"));
        var starts = new java.util.HashMap<>(chunk.getAllStarts());
        var piece = new net.minecraft.world.level.levelgen.structure.StructurePiece(
                net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType.IGLOO, 0,
                new net.minecraft.world.level.levelgen.structure.BoundingBox(in.getX() - 1, in.getY() - 1, in.getZ() - 1,
                        in.getX() + 1, in.getY() + 3, in.getZ() + 1)) {
            @Override
            protected void addAdditionalSaveData(net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext ctx,
                    CompoundTag tag) {}

            @Override
            public void postProcess(net.minecraft.world.level.WorldGenLevel l, net.minecraft.world.level.StructureManager sm,
                    net.minecraft.world.level.chunk.ChunkGenerator gen, net.minecraft.util.RandomSource rnd,
                    net.minecraft.world.level.levelgen.structure.BoundingBox bb, ChunkPos cp, BlockPos pivot) {}
        };
        var start = new net.minecraft.world.level.levelgen.structure.StructureStart(structure, chunk.getPos(), 0,
                new net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer(List.of(piece)));
        chunk.setStartForStructure(structure, start);
        // Like real generation: every chunk the (inflated) box touches references the start.
        java.util.Map<net.minecraft.world.level.chunk.LevelChunk, java.util.Map<net.minecraft.world.level.levelgen.structure.Structure,
                it.unimi.dsi.fastutil.longs.LongSet>> saved = new java.util.HashMap<>();
        for (int cx = (in.getX() - 4) >> 4; cx <= (in.getX() + 6) >> 4; cx++) {
            for (int cz = (in.getZ() - 4) >> 4; cz <= (in.getZ() + 4) >> 4; cz++) {
                var c = level.getChunk(cx, cz);
                saved.put(c, new java.util.HashMap<>(c.getAllReferences()));
                c.addReferenceForStructure(structure, chunk.getPos().toLong());
            }
        }
        try {
            h.assertTrue(WorldGenHandler.insideAnyStructure(level, in, 0), "inside a structure box not detected");
            h.assertFalse(WorldGenHandler.insideAnyStructure(level, in.east(3), 0), "3 blocks out counted as inside");
            h.assertTrue(WorldGenHandler.insideAnyStructure(level, in.east(3), 2), "margin 2 not applied");
            // A job whose only candidate is inside the box places nothing (and queues nothing: the chunk is loaded).
            int before = ManholeData.get(level.getServer()).nodes().size();
            WorldGenHandler.runJob(level, new WorldGenHandler.Job("manholes_test:test_villages", "minecraft:igloo",
                    in.getY() - 30, in.getY() + 30, List.of(PendingPlacement.col(in.getX(), in.getZ())), Direction.NORTH));
            h.assertValueEqual(ManholeData.get(level.getServer()).nodes().size(), before, "a manhole was placed inside a structure");
        } finally {
            chunk.setAllStarts(starts);
            saved.forEach((c, m) -> c.setAllReferences(m));
        }
        h.succeed();
    }

    /** generation.structureBlacklist / biomeBlacklist / dimensionBlacklist, defaults and live changes. */
    @GameTest(templateNamespace = NS, template = TEMPLATE, batch = "blacklist")
    public static void blacklist(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        var server = level.getServer();
        h.assertTrue(it.ratlab.manholes.gen.Blacklist.structure(level, "minecraft:stronghold"), "stronghold");
        h.assertTrue(it.ratlab.manholes.gen.Blacklist.structure(level, "minecraft:mineshaft_mesa"), "#minecraft:mineshaft");
        h.assertTrue(it.ratlab.manholes.gen.Blacklist.structure(level, "minecraft:shipwreck_beached"), "#minecraft:shipwreck");
        h.assertFalse(it.ratlab.manholes.gen.Blacklist.structure(level, "minecraft:village_plains"), "village blacklisted");
        var biomes = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        h.assertTrue(it.ratlab.manholes.gen.Blacklist.biome(biomes.getHolderOrThrow(net.minecraft.world.level.biome.Biomes.DEEP_OCEAN), biomes),
                "#minecraft:is_ocean");
        h.assertTrue(it.ratlab.manholes.gen.Blacklist.biome(biomes.getHolderOrThrow(net.minecraft.world.level.biome.Biomes.RIVER), biomes),
                "#minecraft:is_river");
        h.assertFalse(it.ratlab.manholes.gen.Blacklist.biome(biomes.getHolderOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS), biomes),
                "plains blacklisted");
        ServerLevel nether = server.getLevel(net.minecraft.world.level.Level.NETHER);
        h.assertTrue(nether != null && it.ratlab.manholes.gen.Blacklist.dimension(nether), "nether");
        h.assertFalse(it.ratlab.manholes.gen.Blacklist.dimension(server.overworld()), "overworld blacklisted");
        // No rule rolls in a blacklisted dimension (scatter rules included).
        h.assertTrue(WorldGenHandler.rollChunk(nether, nether.getChunk(0, 0)).isEmpty(), "rules rolled in the nether");
        // Live change: blacklist villages; a queued village job is dropped.
        var old = ManholesConfig.STRUCTURE_BLACKLIST.get();
        ManholesConfig.STRUCTURE_BLACKLIST.set(List.of("#minecraft:village"));
        try {
            h.assertTrue(it.ratlab.manholes.gen.Blacklist.structure(level, "minecraft:village_plains"), "#minecraft:village");
            h.assertFalse(it.ratlab.manholes.gen.Blacklist.structure(level, "minecraft:stronghold"), "list not replaced");
            BlockPos a = h.absolutePos(new BlockPos(0, 2, 0));
            int before = ManholeData.get(server).nodes().size();
            WorldGenHandler.runJob(level, new WorldGenHandler.Job("manholes_test:test_villages", "minecraft:village_plains",
                    a.getY() - 3, a.getY() + 3, List.of(PendingPlacement.col(a.getX(), a.getZ())), Direction.NORTH));
            h.assertValueEqual(ManholeData.get(server).nodes().size(), before, "blacklisted structure still generated");
        } finally {
            ManholesConfig.STRUCTURE_BLACKLIST.set(old);
        }
        h.succeed();
    }

    /**
     * The cover renderer's lid transform uses vanilla's element-rotation convention (FaceBakery.applyElementRotation:
     * Quaternionf.rotationAxis(rad, axis) about the origin, right-hand rule); angles beyond 45 degrees work.
     */
    @GameTest(templateNamespace = NS, template = TEMPLATE)
    public static void lidRotationConvention(GameTestHelper h) {
        org.joml.Vector3f origin = new org.joml.Vector3f(8, 1.6f, 17);
        float[][] points = {{0, 2, 16}, {16, 2, 0}, {3, 0.5f, 9}};
        float[] angles = {45f, -45f, 100f, -100f, 22.5f};
        char[] axes = {'x', 'y', 'z'};
        for (char ax : axes) {
            for (float ang : angles) {
                for (float[] pt : points) {
                    // Vanilla, copied: rotateVertexBy(pos, origin, new Matrix4f().rotation(quaternion), scale 1)
                    org.joml.Vector3f o = new org.joml.Vector3f(origin).div(16f);
                    org.joml.Vector3f v = new org.joml.Vector3f(pt[0], pt[1], pt[2]).div(16f);
                    org.joml.Quaternionf q = new org.joml.Quaternionf().rotationAxis(ang * (float) (Math.PI / 180.0),
                            it.ratlab.manholes.client.cover.LidMath.axis(ax));
                    org.joml.Vector4f r4 = new org.joml.Matrix4f().rotation(q).transform(new org.joml.Vector4f(v.x - o.x, v.y - o.y, v.z - o.z, 1f));
                    org.joml.Vector3f vanilla = new org.joml.Vector3f(r4.x + o.x, r4.y + o.y, r4.z + o.z);
                    org.joml.Vector4f ours4 = it.ratlab.manholes.client.cover.LidMath.partMatrix(ax, ang, origin, null, 1f)
                            .transform(new org.joml.Vector4f(v, 1f));
                    h.assertTrue(Math.abs(ours4.x - vanilla.x) < 1e-5f && Math.abs(ours4.y - vanilla.y) < 1e-5f
                            && Math.abs(ours4.z - vanilla.z) < 1e-5f, "axis " + ax + " " + ang + " deg: " + ours4 + " vs " + vanilla);
                }
            }
        }
        // Right-hand rule sanity: +90 about x turns +y into +z.
        org.joml.Vector4f up = it.ratlab.manholes.client.cover.LidMath.partMatrix('x', 90f, new org.joml.Vector3f(), null, 1f)
                .transform(new org.joml.Vector4f(0, 1, 0, 1));
        h.assertTrue(Math.abs(up.z - 1f) < 1e-5f && Math.abs(up.y) < 1e-5f, "right-hand rule: " + up);
        // Rotation first, then the translation (scaled by t).
        org.joml.Vector4f tr = it.ratlab.manholes.client.cover.LidMath.partMatrix('y', 0f, origin, new org.joml.Vector3f(16, 0, 0), 0.5f)
                .transform(new org.joml.Vector4f(0, 0, 0, 1));
        h.assertTrue(Math.abs(tr.x - 0.5f) < 1e-5f, "translate * t: " + tr);
        h.succeed();
    }

    /**
     * 1.6.0: the block entity's update tag / packet carries the effective {@code rust} (homes 0), a client reads it back,
     * and the look loader parses {@code condition_overlays} (the shipped look files, and bad entries skipped).
     */
    @GameTest(templateNamespace = NS, template = TEMPLATE, batch = "pry_rust")
    public static void conditionOverlaySync(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos abs = h.absolutePos(new BlockPos(0, 2, 2));
        NodeRecord r = ManholesAPI.place(level, abs, "Cond", null, false, null);
        h.assertTrue(r != null, "place failed");
        ManholeBlockEntity be = (ManholeBlockEntity) level.getBlockEntity(abs);
        be.setRust(3);
        CompoundTag upd = be.getUpdateTag(level.registryAccess());
        h.assertTrue(upd.contains("rust"), "update tag has rust: " + upd);
        h.assertValueEqual(upd.getInt("rust"), 3, "update tag rust");
        h.assertValueEqual(upd.size(), 1, "update tag carries only rust: " + upd);
        var pkt = be.getUpdatePacket();
        h.assertTrue(pkt != null && pkt.getTag().getInt("rust") == 3, "update packet carries rust");
        ManholeBlockEntity client = new ManholeBlockEntity(abs, be.getBlockState());
        client.handleUpdateTag(upd, level.registryAccess());
        h.assertValueEqual(client.rust(), 3, "client reads rust from the update tag");
        ManholesAPI.remove(level, abs);

        BlockPos home = new BlockPos(2, 2, 2);
        h.setBlock(home, ModRegistry.HOME_MANHOLE.get());
        ManholeBlockEntity hbe = h.getBlockEntity(home);
        h.assertValueEqual(hbe.getUpdateTag(level.registryAccess()).getInt("rust"), 0, "home update tag rust 0");
        h.setBlock(home, Blocks.AIR);

        // Shipped look files: every condition overlay names an existing part and a model file that exists.
        for (String look : List.of("city", "grate", "hatch", "cave", "home_manhole")) {
            String path = "/assets/manholes/looks/" + look + ".json";
            try (java.io.InputStream in = ManholeGameTests.class.getResourceAsStream(path)) {
                h.assertTrue(in != null, "missing " + path);
                var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                        .getAsJsonObject();
                int parts = json.has("lid_parts") ? json.getAsJsonArray("lid_parts").size() : 0;
                var ovs = it.ratlab.manholes.client.cover.LookOverlays.parse(look, json, parts);
                if (look.equals("home_manhole")) {
                    h.assertTrue(ovs.isEmpty(), "home_manhole has no overlays");
                    continue;
                }
                h.assertTrue(!ovs.isEmpty(), look + " has condition overlays");
                int declared = json.getAsJsonArray("condition_overlays").size();
                h.assertValueEqual(ovs.size(), declared, look + ": every declared overlay parsed");
                for (var ov : ovs) {
                    h.assertTrue(ov.level(0) == null, look + ": level 0 draws nothing");
                    h.assertTrue(ov.part() >= 0 && ov.part() < parts, look + " overlay part in range");
                    for (int lv = 1; lv <= 3; lv++) {
                        var m = ov.level(lv);
                        h.assertTrue(m != null, look + " part " + ov.part() + " level " + lv);
                        String mf = "/assets/" + m.getNamespace() + "/models/" + m.getPath() + ".json";
                        h.assertTrue(ManholeGameTests.class.getResource(mf) != null, "missing overlay model " + mf);
                    }
                }
            } catch (java.io.IOException ex) {
                h.fail("read " + path + ": " + ex);
            }
        }
        // Inline: a valid entry, a part out of range and a bad level are skipped; the look still loads.
        var inline = com.google.gson.JsonParser.parseString("""
                {"base_closed": "manholes:block/a", "lid_parts": [{"model": "manholes:block/b"}],
                 "condition_overlays": [
                   {"part": 0, "levels": {"1": "manholes:block/c1", "3": "manholes:block/c3"}},
                   {"part": 5, "levels": {"1": "manholes:block/x"}},
                   {"part": 0, "levels": {"4": "manholes:block/y"}}]}
                """).getAsJsonObject();
        var il = it.ratlab.manholes.client.cover.LookOverlays.parse("inline", inline, 1);
        h.assertValueEqual(il.size(), 1, "bad overlay entries skipped");
        h.assertValueEqual(il.get(0).part(), 0, "part index");
        h.assertValueEqual(il.get(0).level(1).toString(), "manholes:block/c1", "level 1 model");
        h.assertTrue(il.get(0).level(2) == null, "missing level 2 draws nothing");
        h.assertValueEqual(il.get(0).level(3).toString(), "manholes:block/c3", "level 3 model");
        var none = com.google.gson.JsonParser.parseString("{\"base_closed\": \"manholes:block/a\"}").getAsJsonObject();
        h.assertTrue(it.ratlab.manholes.client.cover.LookOverlays.parse("none", none, 0).isEmpty(), "no field, no overlays");
        h.assertTrue(it.ratlab.manholes.client.cover.LookOverlays.parseBase("none", none).isEmpty(), "no field, no base overlays");

        // 1.7.0: base_condition_overlays. Shipped: hatch has closed + open, levels 1..3, every model file exists.
        for (String look : List.of("city", "grate", "hatch", "cave", "home_manhole")) {
            String path = "/assets/manholes/looks/" + look + ".json";
            try (java.io.InputStream in = ManholeGameTests.class.getResourceAsStream(path)) {
                var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                        .getAsJsonObject();
                var base = it.ratlab.manholes.client.cover.LookOverlays.parseBase(look, json);
                if (look.equals("hatch")) {
                    h.assertTrue(!base.closed().isEmpty() && !base.open().isEmpty(), "hatch has base overlays (closed + open)");
                }
                if (look.equals("home_manhole")) {
                    h.assertTrue(base.isEmpty(), "home_manhole has no base overlays");
                }
                for (boolean openBase : new boolean[] {false, true}) {
                    var side = openBase ? base.open() : base.closed();
                    if (side.isEmpty()) {
                        continue;
                    }
                    h.assertTrue(base.level(openBase, 0) == null, look + ": base level 0 draws nothing");
                    for (int lv = 1; lv <= 3; lv++) {
                        var m = base.level(openBase, lv);
                        h.assertTrue(m != null, look + " base " + (openBase ? "open" : "closed") + " level " + lv);
                        String mf = "/assets/" + m.getNamespace() + "/models/" + m.getPath() + ".json";
                        h.assertTrue(ManholeGameTests.class.getResource(mf) != null, "missing base overlay model " + mf);
                        checkRenderType(h, mf);
                    }
                }
                for (var ov : it.ratlab.manholes.client.cover.LookOverlays.parse(look, json,
                        json.has("lid_parts") ? json.getAsJsonArray("lid_parts").size() : 0)) {
                    for (int lv = 1; lv <= 3; lv++) {
                        var m = ov.level(lv);
                        if (m != null) {
                            checkRenderType(h, "/assets/" + m.getNamespace() + "/models/" + m.getPath() + ".json");
                        }
                    }
                }
            } catch (java.io.IOException ex) {
                h.fail("read " + path + ": " + ex);
            }
        }
        var inlineBase = com.google.gson.JsonParser.parseString("""
                {"base_closed": "manholes:block/a",
                 "base_condition_overlays": {"closed": {"1": "manholes:block/bc1", "5": "manholes:block/x", "2": "Bad Id!"},
                                             "open": "not an object"}}
                """).getAsJsonObject();
        var ib = it.ratlab.manholes.client.cover.LookOverlays.parseBase("inline", inlineBase);
        h.assertValueEqual(ib.closed().size(), 1, "bad base overlay levels skipped: " + ib.closed());
        h.assertValueEqual(ib.level(false, 1).toString(), "manholes:block/bc1", "base closed level 1");
        h.assertTrue(ib.open().isEmpty(), "malformed open side = none");
        h.assertTrue(it.ratlab.manholes.client.cover.LookOverlays.parseBase("bad",
                com.google.gson.JsonParser.parseString("{\"base_condition_overlays\": 3}").getAsJsonObject()).isEmpty(),
                "non-object field = none");
        h.succeed();
    }

    /** An overlay model's render_type, when declared, is one the renderer handles (cutout or translucent). */
    private static void checkRenderType(GameTestHelper h, String modelFile) {
        try (java.io.InputStream in = ManholeGameTests.class.getResourceAsStream(modelFile)) {
            if (in == null) {
                return;
            }
            var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject();
            if (json.has("render_type")) {
                String rt = json.get("render_type").getAsString();
                h.assertTrue(List.of("minecraft:cutout", "cutout", "minecraft:translucent", "translucent", "minecraft:cutout_mipped",
                        "cutout_mipped").contains(rt), modelFile + ": unexpected render_type " + rt);
            }
        } catch (java.io.IOException ex) {
            h.fail("read " + modelFile + ": " + ex);
        }
    }

    // ---------------------------------------------------------------- 1.7.0

    /** Wide outline / collision / interaction shape on every cover but the hatch; placing next to a cover works. */
    @GameTest(templateNamespace = NS, template = TEMPLATE)
    public static void coverShapes(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos rel = new BlockPos(1, 2, 1); // replaces the sample manhole; everything stays in the 3x3 footprint
        BlockPos abs = h.absolutePos(rel);
        for (ManholeBlock b : ManholeBlock.all()) {
            var state = b.defaultBlockState();
            var shape = state.getShape(level, abs);
            var box = shape.bounds();
            boolean hatch = b == ModRegistry.HATCH.get();
            double lo = hatch ? 0.0 : -0.25;
            double hi = hatch ? 1.0 : 1.25;
            String id = b.id();
            h.assertTrue(Math.abs(box.minX - lo) < 1e-6 && Math.abs(box.maxX - hi) < 1e-6
                    && Math.abs(box.minZ - lo) < 1e-6 && Math.abs(box.maxZ - hi) < 1e-6, id + " outline x/z bounds " + box);
            h.assertTrue(Math.abs(box.minY) < 1e-6 && Math.abs(box.maxY - 0.125) < 1e-6, id + " height 2 px " + box);
            h.assertTrue(state.getCollisionShape(level, abs).bounds().equals(box), id + " collision == outline");
            h.assertTrue(state.getBlock() instanceof ManholeBlock mb && mb.coverShape().bounds().equals(box), id + " coverShape");
            h.assertValueEqual(state.hasLargeCollisionShape(), !hatch, id + " large collision shape");
        }
        // Interaction shape (protected in Block): through the state.
        for (int dx = -1; dx <= 1; dx++) {
            h.setBlock(rel.offset(dx, -1, 0), Blocks.STONE);
            h.setBlock(rel.offset(dx, 0, 0), Blocks.AIR);
            h.setBlock(rel.offset(dx, 1, 0), Blocks.AIR);
        }
        h.setBlock(rel, ModRegistry.CITY_MANHOLE.get());
        var placed = level.getBlockState(abs);
        h.assertTrue(placed.getInteractionShape(level, abs).bounds().equals(placed.getShape(level, abs).bounds()), "interaction == outline");
        // Placing a block right next to the cover (inside its overhang) still works.
        BlockPos nextRel = rel.east();
        h.setBlock(nextRel.below(), Blocks.STONE);
        h.setBlock(nextRel, Blocks.AIR);
        ServerPlayer p = player(h);
        p.setPos(abs.getX() + 5.5, abs.getY() + 3, abs.getZ() + 5.5);
        ItemStack stack = new ItemStack(Items.STONE, 4);
        BlockPos nextAbs = h.absolutePos(nextRel);
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(nextAbs.below()).add(0, 0.5, 0),
                Direction.UP, nextAbs.below(), false);
        var ctx = new net.minecraft.world.item.context.BlockPlaceContext(level, p, InteractionHand.MAIN_HAND, stack, hit);
        var result = ((net.minecraft.world.item.BlockItem) Items.STONE).place(ctx);
        h.assertTrue(result.consumesAction(), "placing next to a cover: " + result);
        h.assertTrue(level.getBlockState(nextAbs).is(Blocks.STONE), "stone placed next to the cover");
        h.assertTrue(level.getBlockState(abs).getBlock() == ModRegistry.CITY_MANHOLE.get(), "cover untouched");
        // A player-sized box standing beside the cover (on the floor, outside the overhang) doesn't collide with it.
        var beside = new net.minecraft.world.phys.AABB(abs.getX() - 0.9, abs.getY(), abs.getZ() + 0.2, abs.getX() - 0.3, abs.getY() + 1.8,
                abs.getZ() + 0.8);
        h.assertTrue(level.noCollision(beside), "no collision beside the overhang");
        var inOverhang = beside.move(0.2, 0, 0); // maxX = -0.1 + 1: overlaps the 4 px overhang
        h.assertTrue(!level.noCollision(inOverhang), "overhang collides");
        h.setBlock(nextRel, Blocks.AIR);
        h.setBlock(rel, Blocks.AIR);
        h.succeed();
    }

    /** Fluids never wash a cover away; covers are immovable (push reaction, relocation tags). */
    @GameTest(templateNamespace = NS, template = TEMPLATE, timeoutTicks = 100)
    public static void coverFluidProof(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos cover = new BlockPos(1, 2, 1); // replaces the sample manhole
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                h.setBlock(cover.offset(dx, -1, dz), Blocks.STONE);
                h.setBlock(cover.offset(dx, 0, dz), Blocks.AIR);
                h.setBlock(cover.offset(dx, 1, dz), Blocks.AIR);
            }
        }
        h.setBlock(cover, ModRegistry.GRATE.get());
        for (ManholeBlock b : ManholeBlock.all()) {
            var st = b.defaultBlockState();
            String id = b.id();
            h.assertTrue(st.blocksMotion(), id + " blocksMotion (flowing fluids treat it as solid)");
            h.assertTrue(!st.canBeReplaced(net.minecraft.world.level.material.Fluids.WATER), id + " not replaceable by water");
            h.assertTrue(!st.canBeReplaced(net.minecraft.world.level.material.Fluids.LAVA), id + " not replaceable by lava");
            h.assertTrue(st.getPistonPushReaction() == net.minecraft.world.level.material.PushReaction.BLOCK, id + " push reaction BLOCK");
            for (String tag : List.of("c:relocation_not_supported", "mekanism:cardboard_blacklist", "create:non_movable")) {
                h.assertTrue(st.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, ResourceLocation.parse(tag))),
                        id + " in #" + tag);
            }
        }
        // Water sources above and next to the cover (every cover shares the mechanism: blocksMotion, asserted above).
        h.setBlock(cover.above(), Blocks.WATER);
        h.setBlock(cover.east(), Blocks.WATER);
        h.runAfterDelay(12, () -> {
            h.assertBlockPresent(ModRegistry.GRATE.get(), cover);
            h.assertTrue(level.getFluidState(h.absolutePos(cover)).isEmpty(), "no water inside the cover");
            h.assertTrue(!level.getFluidState(h.absolutePos(cover.east().north())).isEmpty()
                    || !level.getFluidState(h.absolutePos(cover.east().south())).isEmpty(), "water did flow (beside the cover)");
            // Clean up the water so it doesn't spread into other tests.
            for (BlockPos b : List.of(cover)) {
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dy = -1; dy <= 2; dy++) {
                        for (int dz = -3; dz <= 3; dz++) {
                            BlockPos q = b.offset(dx, dy, dz);
                            if (!level.getFluidState(h.absolutePos(q)).isEmpty()) {
                                h.setBlock(q, Blocks.AIR);
                            }
                        }
                    }
                }
                h.setBlock(b, Blocks.AIR);
            }
            h.succeed();
        });
    }

    /** 1.7.0 built-in scatter rules: they parse, every biome / block tag they name exists, and they run with naturalSpawn. */
    @GameTest(templateNamespace = NS, template = TEMPLATE)
    public static void wildScatterRules(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        var biomes = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        var blocks = net.minecraft.core.registries.BuiltInRegistries.BLOCK;
        record R(String id, String block, String sampleBiome, String sampleGround) {}
        for (R r : List.of(new R("wild_cave_holes", "manholes:cave_hole", "minecraft:stony_peaks", "minecraft:stone"),
                new R("wild_drain_grates", "manholes:grate", "minecraft:mangrove_swamp", "minecraft:mud"),
                new R("wild_hatches", "manholes:hatch", "minecraft:plains", "minecraft:grass_block"))) {
            String path = "/data/manholes/manholes/builtin_spawn_rule/" + r.id() + ".json";
            try (java.io.InputStream in = ManholeGameTests.class.getResourceAsStream(path)) {
                h.assertTrue(in != null, "missing " + path);
                var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                        .getAsJsonObject();
                var rule = it.ratlab.manholes.gen.SpawnRule.fromJson(Manholes.id(r.id()), json);
                h.assertTrue(rule.type() == it.ratlab.manholes.gen.SpawnRule.Type.SCATTER, r.id() + " is scatter");
                h.assertValueEqual(rule.block() == null ? "" : rule.block().id(), r.block(), r.id() + " block");
                for (var e : json.get("biomes").isJsonArray() ? json.getAsJsonArray("biomes").asList() : List.of(json.get("biomes"))) {
                    String s = e.getAsString();
                    if (s.startsWith("#")) {
                        var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BIOME, ResourceLocation.parse(s.substring(1)));
                        h.assertTrue(biomes.getTag(tag).map(t -> t.size() > 0).orElse(false), r.id() + ": biome tag " + s + " is empty / missing");
                    } else {
                        h.assertTrue(biomes.containsKey(ResourceLocation.parse(s)), r.id() + ": unknown biome " + s);
                    }
                }
                for (var e : json.getAsJsonArray("on_blocks")) {
                    String s = e.getAsString();
                    if (s.startsWith("#")) {
                        var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, ResourceLocation.parse(s.substring(1)));
                        h.assertTrue(blocks.getTag(tag).map(t -> t.size() > 0).orElse(false), r.id() + ": block tag " + s + " is empty / missing");
                    } else {
                        h.assertTrue(blocks.containsKey(ResourceLocation.parse(s)), r.id() + ": unknown block " + s);
                    }
                }
                var bm = it.ratlab.manholes.gen.IdMatcher.parse(json.get("biomes"));
                h.assertTrue(bm.matches(biomes.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.BIOME, ResourceLocation.parse(r.sampleBiome()))), biomes), r.id() + " matches " + r.sampleBiome());
                var gm = it.ratlab.manholes.gen.IdMatcher.parse(json.get("on_blocks"));
                h.assertTrue(gm.matches(blocks.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.BLOCK, ResourceLocation.parse(r.sampleGround()))), blocks), r.id() + " on " + r.sampleGround());
                h.assertValueEqual(SpawnRuleManager.ids().contains(Manholes.id(r.id())), ManholesConfig.b(ManholesConfig.NATURAL_SPAWN),
                        r.id() + " active == naturalSpawn");
            } catch (java.io.IOException ex) {
                h.fail("read " + path + ": " + ex);
            }
        }
        h.succeed();
    }

    /** Arriving home is safe by default (ambushAtHome = false); world covers can ambush. */
    @GameTest(templateNamespace = NS, template = TEMPLATE)
    public static void ambushSkipsHomeByDefault(GameTestHelper h) {
        NodeRecord world = new NodeRecord(UUID.randomUUID(), h.getLevel().dimension(), h.absolutePos(SAMPLE_MANHOLE));
        NodeRecord home = new NodeRecord(UUID.randomUUID(), h.getLevel().dimension(), h.absolutePos(SAMPLE_MANHOLE));
        home.home = true;
        h.assertTrue(TravelHandler.ambushAllowedAt(world), "world manhole should allow ambushes");
        h.assertTrue(!ManholesConfig.b(ManholesConfig.AMBUSH_AT_HOME), "ambushAtHome default should be false");
        h.assertTrue(!TravelHandler.ambushAllowedAt(home), "home manhole must be safe by default");
        h.succeed();
    }
}
