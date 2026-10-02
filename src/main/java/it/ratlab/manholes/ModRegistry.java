// SPDX-License-Identifier: MIT
package it.ratlab.manholes;

import it.ratlab.manholes.block.ManholeBlock;
import it.ratlab.manholes.block.ManholeBlockEntity;
import it.ratlab.manholes.item.CrowbarItem;
import it.ratlab.manholes.recipe.DefaultRecipesCondition;
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
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRegistry {
    public static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, Manholes.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Manholes.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Manholes.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Manholes.MOD_ID);

    public static final RegistryObject<ManholeBlock> HOME_MANHOLE = BLOCKS.register("home_manhole",
            () -> new ManholeBlock("home_manhole", true, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).sound(SoundType.METAL)
                    .strength(3.0f, 6.0f).requiresCorrectToolForDrops()
                    .pushReaction(PushReaction.BLOCK).forceSolidOn()));
    public static final RegistryObject<ManholeBlock> HATCH = worldCover("hatch", "hatch", MapColor.WOOD, SoundType.WOOD);
    public static final RegistryObject<ManholeBlock> GRATE = worldCover("grate", "grate", MapColor.METAL, SoundType.METAL);
    public static final RegistryObject<ManholeBlock> CAVE_HOLE = worldCover("cave_hole", "cave", MapColor.STONE, SoundType.STONE);
    public static final RegistryObject<ManholeBlock> CITY_MANHOLE = worldCover("city_manhole", "city", MapColor.METAL, SoundType.METAL);

    public static final RegistryObject<BlockItem> HOME_MANHOLE_ITEM = ITEMS.register("home_manhole", () -> new BlockItem(HOME_MANHOLE.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> HATCH_ITEM = ITEMS.register("hatch", () -> new BlockItem(HATCH.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> GRATE_ITEM = ITEMS.register("grate", () -> new BlockItem(GRATE.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> CAVE_HOLE_ITEM = ITEMS.register("cave_hole", () -> new BlockItem(CAVE_HOLE.get(), new Item.Properties()));
    public static final RegistryObject<BlockItem> CITY_MANHOLE_ITEM = ITEMS.register("city_manhole", () -> new BlockItem(CITY_MANHOLE.get(), new Item.Properties()));

    public static final RegistryObject<CrowbarItem> CROWBAR = ITEMS.register("crowbar",
            () -> new CrowbarItem(new Item.Properties().stacksTo(1).durability(ManholesStartupConfig.crowbarDurability())));

    public static final DeferredRegister<Item> TEST_ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Manholes.MOD_ID);
    public static final RegistryObject<Item> TEST_RUSTY_CROWBAR = TEST_ITEMS.register("gametest_rusty_crowbar", () -> new Item(new Item.Properties()));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<ManholeBlockEntity>> MANHOLE_BE =
            BLOCK_ENTITIES.register("manhole", () -> BlockEntityType.Builder
                    .of(ManholeBlockEntity::new, HOME_MANHOLE.get(), HATCH.get(), GRATE.get(),
                            CAVE_HOLE.get(), CITY_MANHOLE.get()).build(null));

    public static final RegistryObject<SoundEvent> SOUND_PRY = sound("manhole.pry");
    public static final RegistryObject<SoundEvent> SOUND_OPEN = sound("manhole.open");
    public static final RegistryObject<SoundEvent> SOUND_INSERT = sound("manhole.insert");
    public static final RegistryObject<SoundEvent> SOUND_LEVER = sound("manhole.lever");
    public static final RegistryObject<SoundEvent> SOUND_SKILL_SUCCESS = sound("manhole.skill_success");
    public static final RegistryObject<SoundEvent> SOUND_SKILL_FAIL = sound("manhole.skill_fail");
    public static final RegistryObject<SoundEvent> SOUND_SLIDE = sound("manhole.slide");
    public static final RegistryObject<SoundEvent> SOUND_LADDER = sound("travel.ladder");
    public static final RegistryObject<SoundEvent> SOUND_FOOTSTEP = sound("travel.footstep");
    public static final RegistryObject<SoundEvent> SOUND_DRIP = sound("travel.drip");
    public static final RegistryObject<SoundEvent> SOUND_ARRIVE = sound("travel.arrive");

    public static final TagKey<Item> PRY_TOOLS = TagKey.create(Registries.ITEM, Manholes.id("pry_tools"));
    public static final TagKey<EntityType<?>> ATTRACTED_BY_NOISE = TagKey.create(Registries.ENTITY_TYPE, Manholes.id("attracted_by_noise"));
    public static final TagKey<EntityType<?>> AMBUSH_MOBS = TagKey.create(Registries.ENTITY_TYPE, Manholes.id("ambush_mobs"));

    private ModRegistry() {}

    private static RegistryObject<ManholeBlock> worldCover(String name, String look, MapColor color, SoundType sound) {
        return BLOCKS.register(name, () -> new ManholeBlock(look, false, BlockBehaviour.Properties.of()
                .mapColor(color).sound(sound)
                .strength(5.0f, 1200.0f).requiresCorrectToolForDrops()
                .pushReaction(PushReaction.BLOCK).forceSolidOn()));
    }

    private static RegistryObject<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Manholes.id(name)));
    }

    static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        SOUNDS.register(bus);
        CraftingHelper.register(DefaultRecipesCondition.Serializer.INSTANCE);
        if (Boolean.getBoolean("manholes.gametests")) {
            TEST_ITEMS.register(bus);
        }
    }
}
