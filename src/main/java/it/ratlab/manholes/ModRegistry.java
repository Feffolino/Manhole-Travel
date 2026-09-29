// SPDX-License-Identifier: MIT
package it.ratlab.manholes;

import it.ratlab.manholes.block.ManholeBlock;
import it.ratlab.manholes.block.ManholeBlockEntity;
import it.ratlab.manholes.item.CrowbarItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Manholes.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Manholes.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Manholes.MOD_ID);
    public static final DeferredRegister<com.mojang.serialization.MapCodec<? extends net.neoforged.neoforge.common.conditions.ICondition>> CONDITIONS =
            DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.CONDITION_CODECS, Manholes.MOD_ID);
    public static final DeferredHolder<com.mojang.serialization.MapCodec<? extends net.neoforged.neoforge.common.conditions.ICondition>,
            com.mojang.serialization.MapCodec<it.ratlab.manholes.recipe.DefaultRecipesCondition>> DEFAULT_RECIPES_CONDITION =
            CONDITIONS.register("default_recipes_enabled", () -> it.ratlab.manholes.recipe.DefaultRecipesCondition.CODEC);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Manholes.MOD_ID);

    public static final DeferredBlock<ManholeBlock> HOME_MANHOLE = BLOCKS.register("home_manhole",
            () -> new ManholeBlock("home_manhole", true, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).sound(SoundType.METAL)
                    .strength(3.0f, 6.0f).requiresCorrectToolForDrops()
                    .pushReaction(PushReaction.BLOCK).forceSolidOn()));
    public static final DeferredBlock<ManholeBlock> HATCH = worldCover("hatch", "hatch", MapColor.WOOD, SoundType.WOOD);
    public static final DeferredBlock<ManholeBlock> GRATE = worldCover("grate", "grate", MapColor.METAL, SoundType.METAL);
    public static final DeferredBlock<ManholeBlock> CAVE_HOLE = worldCover("cave_hole", "cave", MapColor.STONE, SoundType.STONE);
    public static final DeferredBlock<ManholeBlock> CITY_MANHOLE = worldCover("city_manhole", "city", MapColor.METAL, SoundType.METAL);

    public static final DeferredItem<BlockItem> HOME_MANHOLE_ITEM = ITEMS.registerSimpleBlockItem(HOME_MANHOLE);
    public static final DeferredItem<BlockItem> HATCH_ITEM = ITEMS.registerSimpleBlockItem(HATCH);
    public static final DeferredItem<BlockItem> GRATE_ITEM = ITEMS.registerSimpleBlockItem(GRATE);
    public static final DeferredItem<BlockItem> CAVE_HOLE_ITEM = ITEMS.registerSimpleBlockItem(CAVE_HOLE);
    public static final DeferredItem<BlockItem> CITY_MANHOLE_ITEM = ITEMS.registerSimpleBlockItem(CITY_MANHOLE);
    /** Durability comes from the startup config (manholes-startup.toml), loaded before registration. */
    public static final DeferredItem<CrowbarItem> CROWBAR = ITEMS.register("crowbar",
            () -> new CrowbarItem(new Item.Properties().stacksTo(1).durability(ManholesStartupConfig.crowbarDurability())));

    /** Game-test only (registered with -Dmanholes.gametests=true): a foreign-looking crowbar outside #manholes:pry_tools. */
    public static final DeferredRegister.Items TEST_ITEMS = DeferredRegister.createItems(Manholes.MOD_ID);
    public static final DeferredItem<Item> TEST_RUSTY_CROWBAR = TEST_ITEMS.registerSimpleItem("gametest_rusty_crowbar");

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ManholeBlockEntity>> MANHOLE_BE =
            BLOCK_ENTITIES.register("manhole", () -> BlockEntityType.Builder
                    .of(ManholeBlockEntity::new, HOME_MANHOLE.get(), HATCH.get(), GRATE.get(),
                            CAVE_HOLE.get(), CITY_MANHOLE.get()).build(null));

    public static final DeferredHolder<SoundEvent, SoundEvent> SOUND_PRY = sound("manhole.pry");
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUND_OPEN = sound("manhole.open");
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUND_INSERT = sound("manhole.insert");
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUND_LEVER = sound("manhole.lever");
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUND_SKILL_SUCCESS = sound("manhole.skill_success");
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUND_SKILL_FAIL = sound("manhole.skill_fail");
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUND_SLIDE = sound("manhole.slide");
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUND_LADDER = sound("travel.ladder");
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUND_FOOTSTEP = sound("travel.footstep");
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUND_DRIP = sound("travel.drip");
    public static final DeferredHolder<SoundEvent, SoundEvent> SOUND_ARRIVE = sound("travel.arrive");

    /** Items that can pry a manhole open. Default: manholes:crowbar only. */
    public static final TagKey<Item> PRY_TOOLS = TagKey.create(Registries.ITEM, Manholes.id("pry_tools"));
    /** Mobs alerted by the prying noise. Default: #minecraft:undead. */
    public static final TagKey<EntityType<?>> ATTRACTED_BY_NOISE = TagKey.create(Registries.ENTITY_TYPE, Manholes.id("attracted_by_noise"));
    /** Mobs that can ambush a traveller on arrival. Default: empty (no ambush). */
    public static final TagKey<EntityType<?>> AMBUSH_MOBS = TagKey.create(Registries.ENTITY_TYPE, Manholes.id("ambush_mobs"));

    private ModRegistry() {}

    /** A world cover: unbreakable while config 'unbreakable' is on, otherwise breaks and drops nothing. */
    private static DeferredBlock<ManholeBlock> worldCover(String name, String look, MapColor color, SoundType sound) {
        return BLOCKS.register(name, () -> new ManholeBlock(look, false, BlockBehaviour.Properties.of()
                .mapColor(color).sound(sound)
                // Real hardness comes from getDestroyProgress (config 'unbreakable'); this is the breakable value.
                .strength(5.0f, 1200.0f).requiresCorrectToolForDrops()
                // 1.7.0: immovable (pistons; tags c:relocation_not_supported etc. for other mods) and fluid-proof:
                // forceSolidOn makes blocksMotion() true, so FlowingFluid.canHoldFluid never lets water wash it away.
                .pushReaction(PushReaction.BLOCK).forceSolidOn()));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Manholes.id(name)));
    }

    static void register(IEventBus bus) {
        // 1.5.0: manholes:manhole is gone; old worlds, structure NBTs and inventories load it as manholes:city_manhole
        // (same facing / open properties, same block entity type, so the node data is kept).
        BLOCKS.addAlias(Manholes.id("manhole"), Manholes.id("city_manhole"));
        ITEMS.addAlias(Manholes.id("manhole"), Manholes.id("city_manhole"));
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        SOUNDS.register(bus);
        CONDITIONS.register(bus);
        if (Boolean.getBoolean("manholes.gametests")) {
            TEST_ITEMS.register(bus);
        }
    }
}
