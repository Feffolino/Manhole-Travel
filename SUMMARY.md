# Manhole Travel (`manholes`) 1.7.0

NeoForge 1.21.1 mod, MIT. Project layout mirrors `Desktop/omegafe` (MDK-1.21.1-ModDevGradle, MDG 2.0.147, Gradle 9.2.1).
Build: `JAVA_HOME="/c/Program Files/Java/jdk-25" ./gradlew build` produces `build/libs/manholes-1.7.0.jar`.
Public docs: `CURSEFORGE.md` (page text) and `README.md`.

Manhole covers are found around the world. You pry one open while the noise draws the undead, and from then on it's a
fast-travel node of your team's sewer network. The mod has no pack-specific content. Everything specific to a pack lives
in config, tags, datapack JSON and KubeJS.

**Zero required dependencies.** Only NeoForge is needed, and the mod uses only NeoForge config, networking and SavedData.
KubeJS, FTB Teams and FTB Chunks (client) are `type="optional"` in `neoforge.mods.toml` and `compileOnly` in Gradle
(local jars in `libs/`, plus Architectury for FTB Chunks' event type). All their code is in `compat/kubejs`,
`compat/ftbteams` and `compat/ftbchunks`, and those classes load only after a `ModList.isLoaded` check
(`compat/Hooks`, `client/ManholesClient`). KubeJS finds the plugin through `kubejs.plugins.txt`.

## 1.7.0 changes
- **Covers in the wild** (built-in scatter rules, active only with `naturalSpawn = true`, overworld only, open sky and
  the usual surface checks, `min_distance` 256, named by the fallback `<biome> (x, z)`, since a fixed rule name would
  give every wild cover of a kind the same name in the travel list):
  | rule | block | biomes | on blocks | chance / chunk |
  |---|---|---|---|---|
  | `manholes:wild_cave_holes` | `manholes:cave_hole` | `#minecraft:is_mountain`, `#c:is_mountain`, `minecraft:stony_peaks`, `minecraft:windswept_gravelly_hills` | `#minecraft:base_stone_overworld`, `minecraft:gravel` | 0.004 |
  | `manholes:wild_drain_grates` | `manholes:grate` | `#c:is_swamp` (swamp, mangrove swamp) | `minecraft:grass_block`, `minecraft:mud`, `#minecraft:dirt` | 0.003 |
  | `manholes:wild_hatches` | `manholes:hatch` | `#c:is_plains`, `#minecraft:is_forest` | `#minecraft:dirt`, `minecraft:grass_block` | 0.0025 |
  All tags checked against 1.21.1 vanilla / NeoForge 21.1.252 (`c:is_mountain` = `#minecraft:is_mountain` +
  `#c:is_mountain/peak` + `/slope`). Override or disable one with a datapack file of the same path.
- **Wide hitbox**: outline, collision and interaction shape of every cover except `manholes:hatch` are the model's
  footprint `Block.box(-4, 0, -4, 20, 2, 20)` (`ManholeBlock.coverShape()`); the hatch keeps `0..16`. Vanilla handles
  shapes outside the cell (`hasLargeCollisionShape`, `BlockCollisions` scans the entity box inflated by 1). The ray
  trace only tests the block of each cell the ray crosses, so the overhang is targetable only where the ray also
  crosses the cover's own cell; clicking the ground next to a cover still targets the ground (placing blocks there
  works, game-tested). The overhang is 2 px high, so walking onto it is a normal step-up; standing in it is not pushed
  out (the shape isn't a full block, so it isn't "suffocating"). **Landing** after travel: the neighbour spot is
  shifted 0.1 block away from the cover (`TravelHandler.LANDING_SHIFT`) so the player's 0.6-wide box clears the
  overhang; two wide covers side by side can block each other's neighbours (then the player lands on the cover).
- **Fluid-proof and immovable**: every cover block has `forceSolidOn()`, so `blocksMotion()` is true and
  `FlowingFluid.canHoldFluid` (`!state.blocksMotion()` for a plain block) never lets water or lava flow into / wash
  away a cover; `canBeReplaced(state, Fluid)` returns false (bucket emptying). `PushReaction.BLOCK` on all five blocks
  (pistons). Tags (all entries `{"id": ..., "required": false}`, all five blocks, homes included; homes stay breakable
  by their owner): `#c:relocation_not_supported` (NeoForge's conventional tag, id checked in the NeoForge jar),
  `#mekanism:cardboard_blacklist` (the real id in Mekanism 10.7.19; it also includes `#c:relocation_not_supported`),
  `#create:non_movable`. Carry On is not in RatLab; pack makers add
  `manholes:*` to `blacklist.forbiddenTiles` in `config/carryon-common.toml`.
- **Base condition overlays**: looks may declare
  `"base_condition_overlays": {"closed": {"1": model, "2": ..., "3": ...}, "open": {...}}`; the overlay of the base
  being drawn (closed or open) at the cover's level is drawn right after it, same facing rotation and light. Parsed by
  `LookOverlays.parseBase` (missing field / side = none, bad level or id skipped with a warning), registered and
  bake-checked like the lid overlays (`Look.allOverlayModels()`). Shipped: `hatch` (closed + open).
- **Overlay render type**: overlays are drawn with the render type their model declares (`"render_type"`): cutout
  by default / for solid, `minecraft:translucent` for the soft wood-mould overlays (hatch lid + base).
- Tests: `coverShapes`, `coverFluidProof`, `wildScatterRules`, `conditionOverlaySync` extended (base overlays, render
  types); `travelBlockerAndLanding` removes the sample manhole first (its overhang blocked the destination's
  neighbours). 31 tests in both runs.

## 1.6.0 changes
- **The cover's condition is visible.** Looks can declare `condition_overlays`
  (`[{"part": i, "levels": {"1": model, "2": model, "3": model}}]`; `part` indexes `lid_parts`). For the cover's rust
  level L (1..3) the renderer draws `levels[L]` right after lid part `i`, inside the same pose (same open-animation
  interpolation, facing rotation, light), always with the **cutout** render type (the overlay models are inflated
  shells, +0.02 px, with transparent decal textures `textures/block/cond_{rust,wood,rock}_{1,2,3}.png`). Level 0 or a
  missing entry draws nothing; homes are always 0. Shipped: `city`, `grate`, `hatch` part 0, `cave` parts 0-2 (the
  boulders); `home_manhole` none. Models: `models/block/parts/<look>_lid_<i>_cond_<1|2|3>.json` (art side).
- Parsing is in `client/cover/LookOverlays` (no client classes, so the game test can call it on the dedicated server);
  a bad entry (part out of range, level not 1..3, bad id) is skipped with a warning and the look still loads.
  `CoverLooks` registers every overlay model as an extra model; after baking an overlay that didn't bake is only
  skipped (warning `condition overlays [...] are missing; those overlays are skipped`), the look itself is unaffected.
- **Clients get the rust level**: `ManholeBlockEntity.getUpdateTag` is back, minimal: just `rust` (the effective level,
  0 for homes); `getUpdatePacket` = `ClientboundBlockEntityDataPacket.create(this)`. The client's `handleUpdateTag` /
  `onDataPacket` read only `rust` (nothing else of the node reaches the client this way). `syncToClients()` (a
  `sendBlockUpdated` scheduled as the next server task, so it's safe during chunk loads and NBT loads) runs on
  `setRust`, on first registration (rust derived), and on every server-side NBT load (`/data merge`, block swaps).
  Chunk data carries the update tag, so covers show their level as soon as they load.
- Client config `showConditionOverlays = true` (false = covers always look new).
- Network protocol unchanged (`5`): the update tag is vanilla block-entity sync.
- Test `conditionOverlaySync` (28 tests in both runs).

## 1.5.0 changes (summary; details in the sections below)
- **`manholes:manhole` is gone.** World covers are `hatch`, `grate`, `cave_hole` and `city_manhole` (the default of
  rules without `block`, of `ManholesAPI.place` / `Manholes.place` and of `NodeRecord.lookOrDefault`). Migration is a
  NeoForge **registry alias** (`DeferredRegister.addAlias(manholes:manhole, manholes:city_manhole)` on `BLOCKS` and
  `ITEMS`, in `ModRegistry.register`; NeoForge 21.1.252 `BaseMappedRegistry.resolve` looks the alias up whenever the
  old id isn't registered; `MissingMappingsEvent` / `IdMappingEvent` are deprecated and no longer fired). Chunk
  palettes, structure-template palettes and item stacks all resolve through the registry, so old worlds, NBT structures
  (the test sample NBT still contains `manholes:manhole`) and inventories get `city_manhole` with the same `facing` /
  `open`. The block entity type id stays `manholes:manhole`, so the node data (node id, name, rust, rule...) is kept
  as is. The 1.3.0 `look`-NBT conversion now checks for `city_manhole` (what an old `manholes:manhole` becomes).
  Removed: the block / item, its loot table, its `mineable/pickaxe` entry, lang keys `block.manholes.manhole` and
  `manholes.look.manhole`.
- **Covers spawn around structures** (ring `offset`), never inside any structure; **config blacklists** for
  structures, biomes and dimensions.
- **Personal home manholes** (owner, private / shared with team, owner-only break and base rename, old-save
  migration).
- **Per-look condition wording** (hatch: swollen wood, cave: rubble, iron: rust; homes show none), **per-look map
  icons**, FTB Chunks icons **scaled with the zoom**, the pry HUD draws the **held pry tool**.
- Network protocol `5` (Entry gained home fields, `pry_state` gained look + hand, new `share_home`).

## Features
- **World covers** (`hatch`, `grate`, `cave_hole`, `city_manhole`): flat covers, 2/16 high, with states `facing`
  (horizontal) and `open`. Found in the world or placed in NBT structures, no recipe. Unbreakable in survival while
  `unbreakable = true`; with `unbreakable = false` they break but drop nothing. The block entity stores a stable node id
  (a UUID made on first load) and an optional name.
- **`manholes:home_manhole`**: the player-placeable, craftable one. Default recipe (below): 4 iron ingots in the corners,
  4 stone bricks on the sides, an iron trapdoor in the centre. It drops itself (keeping its name). Since 1.5.0 it's a
  **personal travel point**, see "Home manholes" below.
- **World generation**: structure rules and scatter rules are data-driven (see JSON below). Generated manholes start
  closed. Global spacing is `minDistance`, checked against generated nodes in the registry. Placement is deterministic
  from the world seed.
- **`manholes:crowbar`**: the pry tool. Stack size 1, durability `crowbar.crowbarDurability` in
  `config/manholes-startup.toml` (default 250; a STARTUP config because item properties are read at registration, so a
  change needs a restart), repairable in an anvil with `#c:ingots/iron`, listed in Tools & Utilities. Default recipe: 3 iron ingots
  on a diagonal plus 1 red dye (`" DI" / " I " / "I  "`). The item model `models/item/crowbar.json` comes from the art side.
- **Default recipes** (1.3.0): `data/manholes/recipe/crowbar.json` and `home_manhole.json`, vanilla items only, each
  with `"neoforge:conditions": [{"type": "manholes:default_recipes_enabled"}]`. The condition
  (`recipe/DefaultRecipesCondition`, a `MapCodec.unit` registered in `NeoForgeRegistries.Keys.CONDITION_CODECS`) is true
  while `recipes.enableDefaultRecipes = true`. It's checked when datapacks load, so a change needs `/reload`. Datapacks and
  KubeJS can replace or remove the recipes by id.
- **Pry tools** (1.3.0): `item/PryTools.isPryTool(stack)` is the only check, and it's used for prying
  (`ManholeInteraction`), the client-side no-swing `CONSUME` (`ManholeBlock.useItemOn`), the left-click / attack
  suppression (`PryHud`) and the pose (`CrowbarPose`). A tool counts if it's in `#manholes:pry_tools`, or, with
  `prying.matchAnyCrowbar = true` (the default), if its registry path contains `crowbar` (`somemod:rusty_crowbar`). The
  custom first/third-person pose (`IClientItemExtensions`) can only be registered for `manholes:crowbar`, so other
  crowbars pry with the vanilla held-item pose (the swing and mining suppression still applies to them). On a
  multiplayer client the value is the client's own common config, which only affects the cosmetic suppression; the
  server decides.
- **Prying**: hold right-click on a closed cover with a pry tool (see above).
  Moving more than 1 block, switching items, taking damage or letting go cancels it. Every 20 ticks, mobs from
  `#manholes:attracted_by_noise` within `noiseRadius` target the player and walk to them.
  - `prying.difficulty = simple` is the 1.0 behaviour: hold for `pryTicks`, action-bar progress bar, grinding loop.
  - `normal` / `hard` / `custom` run three phases, all server-side, with right-click held throughout:
    **INSERT** (hold `insertTicks`), **LEVER** (button mashing, below), **SLIDE** (hold `slideTicks`).
  - **LEVER = mash** (1.2.0; replaced the 1.1 skill checks, there are no timing events any more): press the mash key
    (client config `mashKey`: jump by default, or attack) as often and as fast as you can. Each accepted press adds
    `mashPerPress` percent of the bar; every tick without a press the bar loses `mashDecayPerTick` percent, never
    below 0. At 100 % a clunk plays and SLIDE starts.
    - **Anti-autoclicker**: presses are `MashPressPayload`s (no data) counted on the server; at most
      `mashMaxPressesPerSecond` count in any 20-tick window, the rest are ignored. Presses outside LEVER are ignored.
      The client sends one payload per real key press (GLFW press events; holding the key and its key-repeat don't
      count), at most 4 per tick.
    - **Noise**: every `mashNoiseEvery` accepted presses the lever clanks and alerts the undead within `noiseRadius`.
    - **Give-up rule**: if the bar reached `mashGiveUpThreshold` percent and then decays back to 0, the attempt ends:
      a loud slip sound, mobs alerted within `noiseRadius * failNoiseMultiplier`, the tool takes `failDurabilityCost`
      extra damage, "The crowbar slips." and the use key must be released before a new attempt.
      `mashGiveUpThreshold = 100` disables it.
  - **Rust**: every node has a rust level 0-3 (block entity NBT `rust`; derived once from the node UUID with a murmur3
    mix, so NBT or `/data merge block <pos> {rust:3}` can override it). Home manholes are always 0. Each level multiplies
    the INSERT / SLIDE durations by `1 + rustMultiplier * rust` and divides `mashPerPress` by the same factor.
    `rustEnabled = false` treats every cover as rust 0. Simple mode ignores rust.
  - **HUD** (`client/PryHud`, a GUI layer above the crosshair, not a Screen, so the player can look around): the
    `textures/gui/pry_bar.png` sheet, centred about 30 px under the crosshair and drawn 2x on GUI scale 2 or less (when
    the screen is wide enough). Crowbar glyph left of a riveted frame; the fill is a horizontal crop of the amber strip
    (never stretched), the "flash" strip for 3 ticks after each accepted press (plus a +-1-2 px shake for 2 ticks), the
    rust-red "danger" strip while the lever bar decays below 25 % after having been higher. Phase name in bold above,
    rust label ("Rust: none / light / moderate / heavy") below, then the hint: "MASH [key]" with a bobbing key cap that
    shows the bound key's name (`KeyMapping.getTranslatedKeyMessage`), or "HOLD [use key]" in INSERT / SLIDE. The same
    bar is used for all three phases. `PryStatePayload` (phase, progress, rust, accepted presses, danger) refreshes it
    every tick.
  - **Keys while prying**: jump is suppressed (`MovementInputUpdateEvent`), attack / mining is cancelled without a
    swing (`InputEvent.InteractionKeyMappingTriggered`, `setSwingHand(false)`), and jump / attack clicks are drained in
    `ClientTickEvent.Pre` so none reach vanilla, whichever `mashKey` is set.
  - **No swing / mining animation**: the block returns `CONSUME` client-side for a pry tool (no swing on the repeated
    use packets); left-click on a manhole with a pry tool in the main hand never starts block breaking or a swing, even
    when not prying. The crowbar has a custom pose while the local player pries (`client/CrowbarPose`,
    `IClientItemExtensions`): first person via `applyForgeHandTransform` (bar tilted down into the cover), third person
    via `getArmPose` returning `HumanoidModel.ArmPose.MANHOLES_PRY` (a NeoForge enum extension,
    `META-INF/enumextensions.json`, client-only class). Every accepted press gives a short downward "pump" (5 ticks,
    driven by the press count in `PryStatePayload`). Other players see the default pose (their pry state isn't synced).
  - Sounds: `manhole.insert` (chain place), `manhole.lever` (iron door / chain step, every `mashNoiseEvery` presses),
    `manhole.skill_success` (chain hit, now "the lever gives way" at 100 %), `manhole.skill_fail` (anvil land, the
    slip), `manhole.slide` (grindstone); `manhole.pry` still loops in INSERT and in simple mode. The sound ids kept their
    1.1 names so resource packs keep working.
  - On success the cover opens visibly, the node joins the team network, a sound plays, the action bar shows
    `Manhole opened: <name>`, the tool takes 1 durability and the stage is given.
  - Without a pry tool the action bar says `It's rusted shut. You need a crowbar to pry it open.`
- **Covers and looks** (1.3.0):
  - **Every look is its own block** (1.4.0). All are `ManholeBlock` (constructor `look` id + `home` flag, both in the
    block codec) and share the one block entity type `manholes:manhole` (all five are its valid blocks; the block of that
    name was removed in 1.5.0):

    | block | look | kind | sound | tool tag |
    |---|---|---|---|---|
    | `manholes:home_manhole` | `home_manhole` | home (breakable, drops itself) | metal | `mineable/pickaxe` |
    | `manholes:hatch` | `hatch` | world cover | wood | `mineable/axe` |
    | `manholes:grate` | `grate` | world cover | metal | `mineable/pickaxe` |
    | `manholes:cave_hole` | `cave` | world cover | stone | `mineable/pickaxe` |
    | `manholes:city_manhole` | `city` | world cover | metal | `mineable/pickaxe` |

    World covers are all alike: unbreakable while `unbreakable = true`, otherwise they break and drop
    nothing (empty loot tables), pried open with any pry tool, no recipe. Each has a BlockItem in the creative tab.
    Hitbox 1x1, 2/16 tall. `ManholeBlockEntity.look()` and the renderer take the look from the block; the block
    entity has no `look` field any more; since 1.6.0 its update tag carries only `rust` (for the condition overlay). The look is still
    copied into the node record (`NodeRecord.look`, `Entry.look`, `node.look` in KubeJS) for the popup and flavour lines.
  - `ManholeBlock.resolve(s)` maps a look id (`hatch`, `cave`, `manholes:cave`) or a block id (`manholes:hatch`,
    `manholes:cave_hole`, any other mod's `ManholeBlock`) to the block, case-insensitive; anything else is null.
  - **Swap a cover** with `/manholes setblock <node|here> <block>`, `Manholes.setBlock(level, pos, blockId)` or
    `ManholesAPI.setBlock` (both id kinds accepted). `ManholeBlockEntity.swapBlock` keeps facing, open, node id, name,
    rule, structure, rust and network membership (a static `ManholeBlock.swapping` flag stops `onRemove` from dropping
    the node and the new block entity's `onLoad` from registering a fresh one). World cover to home and back works.
    `setLook` (API and KubeJS) is a deprecated alias of `setBlock` that logs a warning once; `''` is a no-op.
  - **Old saves (1.3.0)**: a `look` tag of `hatch`, `grate`, `cave` or `city` (with or without `manholes:`) on a
    `manholes:manhole` block entity is read on load and, one server task later (never during chunk loading), the block
    is swapped for the matching block, keeping facing, open, node id and name; it logs `Converted legacy manhole look`.
    This covers chunk loads, structure NBTs (when the chunk becomes full) and `/data merge`. Any other `look` value
    (or a look on another block) is ignored and dropped on the next save.
  - Spawn rules pick the block: `"look"` (or its alias `"block"`) in the JSON and `.look(s)` / `.block(s)` in the
    builder accept both id kinds; an unknown one throws (the rule fails to load).
  - **Rendering** (`client/cover`): the block uses `RenderShape.ENTITYBLOCK_ANIMATED`, so the chunk mesh draws nothing.
    `ManholeCoverRenderer` (a BER, view distance 256, 3x2x3 cull box) draws the look. The blockstate files are
    name the static models (`variant_<look>` for the new blocks), which are used for particles and as the fallback.
    Parts drawn with the block's default render type (solid) are drawn cutout instead, so ladders, trapdoors and grates
    keep their holes; parts with their own `render_type` keep it.
  - **Look files** `assets/<ns>/looks/<name>.json` (`base_closed`, `base_open` (optional, defaults to `base_closed`),
    `lid_parts[{model, open:{translate, rotate:{axis, angle, origin}}}]`, `duration_ticks` (default 12),
    `sound_open` / `sound_close`). `CoverLooks` reads every look file from the client `ResourceManager` inside
    `ModelEvent.RegisterAdditional`, on every resource reload (F3+T included), and registers all the models they name
    as standalone extra models. It logs `Loaded N manhole looks [...]`, and warns if `manhole` or `home_manhole` is
    missing. After `ModelEvent.BakingCompleted`, a look is only used if all its models baked (not the missing model);
    otherwise it logs an error listing the bad models. A look id that has no usable look logs one warning ("Manhole look
    'x' not found (expected assets/.../looks/x.json)") and renders the block's blockstate model (the static full cover,
    already rotated). A look file that fails to parse logs an error and is skipped.
  - **Animation**: `ManholeBlockEntity.setBlockState` (client) sees the `open` value change and stores
    `animStart = level.getGameTime()`, then plays the look's `sound_open` / `sound_close` locally
    (`SoundEvent.createVariableRangeEvent`, so any sound id defined in a `sounds.json` works). Covers that load already
    open or closed show the end state. Per frame, `raw = (gameTime - animStart + partialTick) / duration_ticks`,
    clamped to 0..1, then smoothstep. Openness is `t` when opening and `1 - t` when closing. `base_open` is drawn while
    `t > 0`, `base_closed` only when it's fully shut. Each lid part is moved in the north-facing model frame
    (pixels / 16): rotate about `origin` by `angle * t` (right-hand rule, like model element rotations), then translate
    by `translate * t`. The whole thing is then rotated by `facing` like the blockstate `y` (north 0, east 90, south 180,
    west 270). Client config `animateCovers = false` snaps to the end state.
  - **Condition overlays** (1.6.0): see "1.6.0 changes"; drawn per lid part inside that part's pose, cutout, client
    config `showConditionOverlays`.
  - Item rendering: the item models point at the static models (`models/item/<block>.json`).
  - **Condition wording** (1.5.0, `client/ConditionText`): the rust level 0-3 and its effects are the same for every
    cover; only the label changes. `manholes.condition.<look>` + `manholes.condition.<look>.<level>`, shown as
    `manholes.condition.format` ("%s: %s") in the pry HUD and the popup: hatch "Swollen wood: dry / swollen / warped /
    jammed", cave "Rubble: loose / packed / wedged / buried", grate and city "Rust: none / light / heavy / seized" (IT
    in `it_it`). A look without these keys falls back to `manholes.hud.rust.<level>`; home manholes show no line.
    `PryStatePayload` carries the look for the HUD.
  - **Map icons per look** (1.5.0, `client/MapIcons`): `<ns>:textures/gui/map_icon_<look>.png` (16x16; `city`,
    `grate`, `hatch`, `cave`, `home_manhole` from the art side), checked once per look through the resource manager
    (cache cleared on resource reload), fallback `map_icon.png`. Used by the travel-screen map (drawn at 16 px, 1:1),
    the popup title row (16 px) and FTB Chunks. Textures are drawn with the default nearest filtering.
  - Lang: `manholes.look.<look>` (`:` becomes `.`) is shown in the travel popup and in the command feedback; the
    fallback is the raw id. The flavour line of a trip is `manholes.travel.flavor.<look>.N` for the look of the nearest
    manhole within 8 blocks of the player when the fade starts (the cover he climbs into), else the generic
    `manholes.travel.flavor.N`. The HUD phase texts stay generic.
- **Network**: any node reaches any other. The owner is the FTB team if FTB Teams is loaded, otherwise the player. The
  world SavedData `data/manholes.dat` (1.5.0: `version: 2`) holds the node registry, the networks and the queued
  placements. What a player can see and travel to is `travel/Access.visible(player)`: his team network (world covers
  and unowned homes) plus the home manholes visible to him (below).
- **Home manholes** (1.5.0, `travel/HomeManholes`, `travel/Access`):
  - **Owner**: `setPlacedBy` stores the placer's UUID and name on the block entity (`owner`, `owner_name`, `shared`
    NBT) and on the node (`NodeRecord.owner / ownerName / shared`, saved in `manholes.dat`); the node is the authority
    (`fill()` copies node -> block entity, or block entity -> node when the node has none). A home no longer joins the
    team network. The item never carries the owner (`removeComponentsFromTag`).
  - **Visibility**: the owner always; if `shared`, also players whose **current** FTB team equals the owner's current
    team (`teamOf(viewer)` vs `teamOf(server, owner)`, so sharing follows team changes). Without FTB Teams there are no
    teams: shared = owner only. Others see nothing (not in the screen, the sync or the FTB Chunks icons) and get
    "A private home manhole of <name>." when they use it; it can't be pried.
  - **Unowned homes** (NBT structures, `h.setBlock`, old saves that couldn't be attributed): count as shared, are visible
    to the networks that had them (legacy membership), anyone can break them, and the **first player who uses one
    claims it** (owner = him, stays shared, "You claimed this home manhole.").
  - **Share with team** (server-authoritative, owner or op only; scripts / console trusted): the popup toggle (owner
    only, sends `ShareHomePayload`), sneak + right-click with an empty hand (action-bar "Home manhole shared with your
    team." / "... is private again."), `/manholes share <node|here> <true|false>` (open to every player, checks owner
    or op level 2), `Manholes.setShared(level, pos, bool)` / `ManholesAPI.setShared`.
  - **Break**: in survival only the owner, or anyone with `home.anyoneCanBreak = true`; creative players and unowned
    homes always. `getDestroyProgress` returns 0 for others (server side) and a `BlockEvent.BreakEvent` listener cancels
    the break ("This home manhole belongs to <name>."), which is the authority.
  - **Base name** (name tag): owner or op only. Team mates rename it for themselves with the travel-screen alias.
  - **Popup**: "Owner: <name>" and a "Private" (yellow) / "Shared with team" (aqua) badge; for the owner a
    "Share with team: ON / OFF" button in the popup button style above Travel (optimistic, confirmed by the sync).
  - **Old saves** (`ManholeData.migrateLegacyHomes`, once at `ServerStartedEvent` for data without `version: 2`): each
    home without owner gets the first network owner that holds it and resolves to one player (a player UUID without
    FTB Teams, found online or in the profile cache; with FTB Teams a team with exactly one member). Parties (several
    members) and unknown ids leave it unowned and shared. Logs "Assigned owners to N home manholes from a pre-1.5.0
    save".
- **Travel**: right-click an opened node (in your network) with an empty hand or any item. The screen is mostly map:
  - **Map** (about three quarters of the width): the real terrain (see "Travel map terrain" below) on a dark
    background, a faint chunk grid when zoomed in, the nodes as the `map_icon.png` sprite (any square size) drawn at 20 px, you
    (yellow dot with your heading), a north arrow and a scale bar. The current node has a green ring, the hovered one a
    white ring, the selected one a gold ring; names show when zoomed in or hovered. Wheel zooms around the cursor,
    left-drag pans. No blurred vanilla background any more (the map is the content).
  - **Sidebar** (26 % of the width, 110-180 px): "You are here" + the current node on top, then compact one-line rows
    (name, and distance + compass direction, or the dimension for other dimensions).
  - **Map icon texture**: `map_icon.png` can be any square size (16x16, 32x32, ...). The travel screen blits it with
    full-UV sizing (`TravelScreen.blitFull`: UV 0..1, drawn at 20 px). The FTB Chunks icon (large map and minimap)
    goes through FTB Library's image icon, which always maps UV 0..1, onto a 16-unit frame scaled to the target size.
    No 32 px size is hard-coded any more.
  - **Popup buttons** (1.3.0): plain fills, no vanilla sprite. Normal `#2b2d31` with a 1 px `#6b6f78` border and
    `#e8e8ea` text; hover `#3a3d44` with an amber `#d9a520` border; pressed `#222327` (amber border, label 1 px down);
    disabled (current node / unreachable) `#1e1f22`, border `#34363b`, text `#55575c`. Labels are drawn without a
    shadow. The Travel button now fires on release over the button (press, then release). The rename pencil uses the
    same style. The popup also shows the look's name.
  - **Popup**: clicking an icon opens a small dark panel with a thin border next to it (kept inside the screen): the
    name with a rename pencil, "Current location" for the node you're at, distance and direction, coordinates,
    dimension, rust level, and its own **Travel** button (disabled for the current node or an unreachable dimension).
    Clicking a sidebar row selects it, centres the map on it and opens the same popup (anchored left of the row for
    nodes in other dimensions). Clicking empty map space or Esc closes the popup; a second Esc closes the screen. The old
    vanilla Travel button at the bottom is gone.
  - **Rename** (pencil, or double-click a row): an inline EditBox in the popup, max 32 characters; Enter confirms, Esc
    cancels, clicking elsewhere confirms. `RenameNodePayload` goes to the server, which checks that the node is in the
    player's network, strips control / formatting characters and stores a **per-owner alias** (team or player) in
    `data/manholes.dat`. The alias wins over the node's own name for that owner only, never changes the node's base
    name, and an empty name clears it. It reaches the list, the popup, the FTB Chunks hover and the arrival message
    through the next `network_sync`. The name tag on a home manhole still sets the base name.
  - With `travel.animationEnabled = true` (default) picking a destination plays a climb:
    - **descent** (`descentTicks`, default 40): the server puts you on the centre of the start cover, facing the
      cover's `facing`; the client camera glides there from where you stood (first quarter), pitches down to 80 degrees
      (second quarter), then climbs 1.5 blocks down with ladder steps (`travel.ladder`) while the fade to black runs;
    - the trip itself: black for `travelFadeTicks`, footsteps, dripping water, a random `manholes.travel.flavor.N`
      line; the server teleports you at the end of it;
    - **ascent** (`ascentTicks`, default 30): fade in with the camera 1.5 blocks below the destination cover looking
      up (-70 degrees), rise (two thirds), then step off to the landing spot and level the head to 0 (last third).
  - With `animationEnabled = false` it's the 1.0 fade: black over `travelFadeTicks`, then the teleport.
  - Only the camera moves (`client/TravelCamera`: angles in `ViewportEvent.ComputeCameraAngles`, position in
    `ViewportEvent.ComputeFov`, which `GameRenderer` fires right after `Camera.setup`, via an access transformer on
    `Camera.setPosition(Vec3)`). The server places the player itself and holds him in place for the whole session,
    and he is invulnerable. Attacks and block / item / entity interactions are cancelled during a trip. The combat
    blocker still applies before a trip starts. Logging out mid-trip puts the player back on his start spot (before
    the teleport) or on the landing spot (after it) before he is saved. The arrival message, the `arrived` event and the
    ambush come after the ascent, when control returns.
  - The player lands on the first free, safe spot next to the destination cover, facing away from it. A spot needs a
    floor (or a low block such as a slab at the feet), two full free blocks above the standing surface (with a slab at
    the feet that means the blocks at +1 and +2) and no collision for a player-sized box.
  - **The whole HUD is hidden during a trip** (`client/TravelUi`): `options.hideGui = true` from the start of the
    descent / fade until the ascent / fade-in is over, the player's own F1 value restored at the end, on cancel and on
    disconnect. The fade overlay is a mod GUI layer, which NeoForge draws regardless of `hideGui`; FTB Chunks'
    minimap checks `hideGui` itself; the hand is hidden too.
  - Costs, ambush, blockers and cooldown are all in the config.
- **Server authoritative**: the screen only shows what the server sent. The server re-checks membership, distance to the
  starting node, dimension, blockers, cooldown and cost for every request.
- **No per-tick scanning**: pry and travel sessions exist only while they run. Generation work is queued on
  `ChunkEvent.Load(isNewChunk)` and drained at the end of the level tick.
- **FTB Chunks map icons** (client, optional): every node in your network shows on FTB Chunks' large map and minimap of
  its dimension with the `manholes:textures/gui/map_icon.png` sprite (any square size); hovering shows the name (your alias if
  set). FTB Chunks sizes icons itself (large map 6 px or more by zoom, minimap its size / 16), so the icon's `draw`
  renders the sprite at a fixed 22 px on the large map and 16 px on the minimap, centred on FTB Chunks' slot, undoing
  the pose scale FTB Chunks applied; `getIconScale` 2.75 on the large map makes the hover area bigger. The server sends
  `NetworkSyncPayload` (your node list with aliases) on login, on dimension change, and at the end of any tick in which a
  node was opened, closed, removed, renamed or aliased; the client then calls `requestMinimapIconRefresh()`. Icons use
  only FTB Chunks' public API (`MapIconEvent.LARGE_MAP` / `MINIMAP`, `MapIcon.SimpleMapIcon`).
- **Travel map terrain**:
  - With FTB Chunks (`compat/ftbchunks/FTBChunksTerrain`, **internal API**, checked against 2101.1.22):
    `MapManager.getInstance()` -> `getDimension(dim)` -> `getLoadedRegions()`; each `MapRegion` is 512x512 blocks with a
    512x512 texture that FTB Chunks renders and uploads lazily. The screen blits `getRenderedMapImageTextureId()` of
    every region intersecting the view once `isMapImageLoaded()` (like FTB Chunks' `MapTileWidget`), at
    `(region * 512 - worldLeft) * scale`, linear filtering when shrunk, nearest when magnified, at most 96 regions
    nearest the view centre. Unexplored pixels are transparent, so they stay dark. Any `Throwable` (a linkage error from
    another FTB Chunks version) disables it for the session with one warning and falls back to the next source.
  - Without FTB Chunks (`client/LoadedChunksTerrain`): when the screen opens, a vanilla-map-style image of the chunks
    the client has loaded around the player (render distance, at most 16 chunks, 10 in ceiling dimensions): top block
    (`WORLD_SURFACE` heightmap; in ceiling dimensions the first floor under the first air gap below the player + 24)
    `MapColor`, shaded by the height step to the north, water shaded by depth, in a `DynamicTexture` freed when the
    screen closes. Unloaded chunks stay transparent; nodes outside the area still show on the dark background.
  - **Why it was grey in 1.1**: there was no terrain source at all. The map pane was a flat `0xF0101418` fill with
    translucent grid lines, drawn over vanilla's blurred in-world menu background, so it read as a grey panel.
- **Stages**: opening a node gives stage `manholes_opened_<rule path>` for generated nodes, or
  `manholes_opened_<node uuid>` for hand-placed or NBT nodes. It uses KubeJS stages, or a scoreboard tag without KubeJS.
  All online team members get it, and players who log in later get the stages of their team's nodes.

## Config (`config/manholes-common.toml`)
| key | default | notes |
|---|---|---|
| `general.unbreakable` | true | world manholes can't be broken in survival (false: breakable, no drop) |
| `generation.naturalSpawn` | **true** (1.3.0; was false) | run the built-in rules in the jar (villages 0.25, pillager outposts 0.5; 1.7.0: wild scatter rules, see 1.7.0 changes) |
| `generation.disableAllGeneration` | false | kill switch for every rule, including datapack and KubeJS ones |
| `generation.minDistance` | 192 | spacing between generated manholes (a rule's `min_distance` overrides it) |
| `generation.placementAttempts` | 24 | candidate spots per structure / per scatter chunk |
| `generation.structureBlacklist` | `#minecraft:mineshaft`, `minecraft:stronghold`, `minecraft:ancient_city`, `minecraft:trial_chambers`, `minecraft:buried_treasure`, `minecraft:monument`, `#minecraft:ocean_ruin`, `#minecraft:shipwreck` | 1.5.0; ids or `#tags`; no structure rule (built-in, datapack, KubeJS) rolls for them; re-checked when a queued placement runs |
| `generation.biomeBlacklist` | `#minecraft:is_ocean`, `#minecraft:is_river` | 1.5.0; ids or `#tags`; the spot's biome, every rule incl. scatter |
| `generation.dimensionBlacklist` | `minecraft:the_nether`, `minecraft:the_end` | 1.5.0; dimension ids; every rule incl. scatter |
| `home.anyoneCanBreak` | false | 1.5.0; true = anyone breaks home manholes in survival |
| `prying.difficulty` | normal | `simple`, `normal`, `hard` or `custom` (presets below) |
| `prying.matchAnyCrowbar` | true | any item whose registry path contains `crowbar` also pries |
| `prying.pryTicks` | 60 | simple mode only |
| `prying.noiseRadius` | 24 | |
| `prying.insertTicks` | 20 | custom only |
| `prying.slideTicks` | 30 | custom only |
| `prying.mashPerPress` | 6.0 | custom only; percent of the LEVER bar per accepted press |
| `prying.mashDecayPerTick` | 0.8 | custom only; percent lost every tick without a press |
| `prying.mashMaxPressesPerSecond` | 12 | normal / hard / custom; presses counted in any 20-tick window |
| `prying.mashNoiseEvery` | 5 | normal / hard / custom; lever clank + mob alert every N presses |
| `prying.mashGiveUpThreshold` | 30.0 | normal / hard / custom; percent; 100 = never give up |
| `prying.failNoiseMultiplier` | 2.0 | the slip alerts mobs within noiseRadius x this |
| `prying.failDurabilityCost` | 3 | extra tool damage on a slip |
| `prying.rustEnabled` | true | normal / hard / custom |
| `prying.rustMultiplier` | 0.25 | insert / slide x (1 + m * rust), mashPerPress / (1 + m * rust) |
| `travel.allowCrossDimension` | false | |
| `travel.travelFadeTicks` | 40 | |
| `travel.timeCostPerKm` | 1000 | only applied when exactly 1 player is online; minimum 1 km |
| `travel.hungerCostPerKm` | 2.0 | exhaustion; minimum 1 km |
| `travel.extraItemCost` | "" | item id or `#tag`, empty = none |
| `travel.extraItemCostCount` | 1 | |
| `travel.ambushChance` | 0.1 | |
| `travel.ambushCountMin` / `ambushCountMax` | 1 / 2 | |
| `travel.combatCooldownTicks` | 100 | |
| `travel.travelCooldownTicks` | 100 | |
| `travel.animationEnabled` | true | climb animation around the fade |
| `travel.descentTicks` | 40 | step 1/4, look down 1/4, climb down 1/2 |
| `travel.ascentTicks` | 30 | rise 2/3, step off 1/3 |
| `recipes.enableDefaultRecipes` | true | condition `manholes:default_recipes_enabled` of the two default recipes |

**Turning natural spawn off (packs)**: ship `config/manholes-common.toml` with `generation.naturalSpawn = false` (an
existing file keeps its value when the default changes, so a pack that already had `false` keeps it). Or leave it on and
override single built-ins with a datapack file of the same path, e.g. `data/manholes/manholes/builtin_spawn_rule/villages.json`
with `"chance": 0`, or a datapack rule `data/manholes/manholes/spawn_rule/villages.json` (same id, it replaces the
built-in). RatLab keeps it off: its `config/manholes-common.toml` already has `naturalSpawn = false`.

Presets (`insertTicks / slideTicks / mashPerPress / mashDecayPerTick`):
- `simple`: hold only (`pryTicks`), as in 1.0.
- `normal`: 20 / 30 / 6 / 0.8 (about 17 presses without decay; at 7 presses/s the lever takes ~3 s, rust 3 ~7 s).
- `hard`: 30 / 40 / 4.5 / 1.0 (the spec asked for lower mashPerPress and higher decay; these values were chosen here:
  ~7 s at 7 presses/s, rust 3 needs about 10 presses/s).
- `custom`: the four keys above.

Removed in 1.2.0: `leverTicks`, `skillChecks`, `skillZoneDegrees`, `skillSpeedDegPerTick`, `skillFailPenalty`,
`skillSuccessBonus`, `maxFails`, `rustZoneShrinkDegrees`. Old toml files still load: NeoForge logs "is not correct.
Correcting" once and drops the unknown keys (seen in the game-test run with a 1.1 config).

Other config files:
- `config/manholes-startup.toml`: `crowbar.crowbarDurability` = 250 (restart needed).
- `config/manholes-client.toml`: `showConditionOverlays` = true (1.6.0), `animateCovers` = true (1.3.0), `mashKey` = `JUMP` (or `ATTACK`). It replaced `skillCheckKey`; an old value is
  dropped and the default JUMP applies.

## Tags
- `manholes:pry_tools` (item). Default: `manholes:crowbar`, plus optional entries (`"required": false`) for
  `#c:tools/crowbars`, `#c:crowbars`, `#forge:tools/crowbars` and `zombie_island:crowbar`. Pickaxes don't pry.
  `matchAnyCrowbar` adds every `*crowbar*` item on top.
- `manholes:attracted_by_noise` (entity_type). Default: `#minecraft:undead`.
- `manholes:ambush_mobs` (entity_type). Default: empty, which disables ambushes.
- `manholes:road_blocks` (block). Default: dirt path, `#c:concretes` (optional), gray and black concrete, stone bricks,
  polished andesite, smooth stone. The test scatter rule uses it.
- Both blocks are in `minecraft:mineable/pickaxe`.
- 1.7.0: every cover block is in `#c:relocation_not_supported`, `#mekanism:cardboard_blacklist` and
  `#create:non_movable` (optional entries).

## Spawn rule JSON
Rules live in `data/<ns>/manholes/spawn_rule/*.json`, which includes `kubejs/data/`, and always apply. Built-in rules
are in `data/manholes/manholes/builtin_spawn_rule/*.json` and apply only when `naturalSpawn = true`. A datapack rule
with the same id replaces a built-in one. Rules reload on `/reload`. Every id or block list below accepts a string or an
array; each entry is an id, a `#tag` or `"*"`.

    { "type": "structure",
      "structures": "#minecraft:village",        // id, #tag or "*"
      "exclude": ["minecraft:village_snowy"],    // optional
      "chance": 0.35,                             // rolled per structure start, per slot
      "max_per_structure": 1,                     // slots (each rolls chance); spacing still applies
      "offset": [6, 24],                          // 1.5.0: ring min..max blocks outside the bounding box (also
                                                  // accepted inside "placement"; a number = min = max)
      "placement": { "surface_only": true, "on_blocks": "#c:stones", "avoid_blocks": "#minecraft:leaves", "margin": 2 },
      "name": {"text": "Old Sewer"},              // text component, or a plain string
      "name_from_structure": true,                // lang key structure.<ns>.<path>, fallback "Prettified Id"
      "block": "manholes:hatch",                  // optional cover block (1.4.0; alias "look"), a block id or a look
                                                  // id ("hatch", "cave"); default manholes:city_manhole
      "min_distance": 192,                        // optional, overrides the config
      "dimensions": ["minecraft:overworld"] }     // optional

    { "type": "scatter", "chance_per_chunk": 0.01, "on_blocks": "#manholes:road_blocks",
      "dimensions": ["minecraft:overworld"], "biomes": "#minecraft:is_overworld", "min_distance": 256, "name": "Storm Drain" }

Placement rules:
- A spot must have a full solid block below, 2 air blocks above it, no fluid on or next to it, and must match
  `on_blocks` and not `avoid_blocks`.
- With `surface_only` (always on for scatter rules) the spot also needs open sky. Without it, the rule scans the
  structure's height, top down.
- **Structure candidates (1.5.0)** lie on a square ring **around** the structure: `placementAttempts` columns, each at
  a whole-block distance d = `minOffset..maxOffset` (default 6..24, `"offset"` / `.offset(min, max)`; per axis, i.e.
  Chebyshev distance from the box edge) outside the bounding box, uniformly along the ring's perimeter. Deterministic
  (same seeded random as before). Y window: the box height +- 24 (terrain around a structure differs); surface spots
  still need `top + 1` within it.
- A structure spot must not be inside **any** structure's bounding box: `WorldGenHandler.insideAnyStructure` asks the
  `StructureManager` for every start referenced by the loaded chunks at the spot (and at the spot +- margin) and
  rejects the spot if one's box, inflated by `margin`, contains it. It never loads a chunk.
- **`margin`** changed meaning in 1.5.0: it used to shrink the box for inside candidates; now it's the clearance the
  spot keeps from every structure's bounding box (the rule's own included, so a margin above minOffset raises the
  effective minimum). RatLab's `.margin(2)` therefore means "at least 2 blocks from any structure".
- Built-in rules (1.5.0): villages place `manholes:hatch`, pillager outposts `manholes:city_manhole`, both
  `offset [6, 24]`.
- Candidates in chunks that aren't generated yet are queued in SavedData and tried when those chunks load. A rule that
  finds no spot logs at debug level and places nothing.

Naming priority: the block entity NBT `name` (from an NBT structure, or `/data merge block <pos> {name:"..."}`), then the
rule's `name` or `name_from_structure`, then `<biome> (x, z)`. A name is plain text, or JSON text if it starts with
`{`, `[` or `"`.

## KubeJS API (server scripts; example: `src/test_datapack/kubejs/server_scripts/manholes_example.js`)
Events group `ManholeEvents`:
- `ManholeEvents.spawnRules(event)` fires on every rule load (server start and `/reload`), after the datapack rules.
  - `event.add(id, json)` takes the datapack schema.
  - `event.structure(id)` and `event.scatter(id)` return builders. Their methods are `structures(...)`, `exclude(...)`,
    `chance(n)`, `maxPerStructure(n)`, `surfaceOnly(b)`, `onBlocks(...)`, `avoidBlocks(...)`, `margin(n)`, `name(s)`,
    `nameFromStructure(b)`, `look(s)` / `block(s)` (1.4.0: picks the cover block, look or block id), `offset(min, max)`
    (1.5.0; swapped arguments are sorted), `minDistance(n)`, `chancePerChunk(n)`, `dimensions(...)` and `biomes(...)`.
  - `event.remove(id)`, `event.modify(id, rule => rule.chance(0.5))` and `event.getIds()`.
  - An id without a namespace becomes `kubejs:<id>`.
- `ManholeEvents.generate(event)` fires for each chosen spot. It has `event.level`, `event.pos`, `event.setPos(pos)`,
  `event.ruleId`, `event.structureId` (or null) and `event.name` (settable, string). `event.cancel()` skips the spot.
- `ManholeEvents.pried(event)` has `event.player`, `event.node`, `event.team` (owner id string) and `event.teamName`.
  `event.cancel()` keeps the cover shut.
- `ManholeEvents.pryPhase(event)` fires when a phased attempt enters a phase: `event.player`, `event.node`,
  `event.phase` (`'insert'`, `'lever'` or `'slide'`) and `event.rust` (0..3, 0 with `rustEnabled = false`).
  `event.cancel()` aborts the attempt ("It won't budge."; the key must be released). Not fired in simple mode.
- `ManholeEvents.mash(event)` fires for every accepted press of the mash key in LEVER, before it's applied:
  `event.player`, `event.node`, `event.progress` (bar before the press, 0..100), `event.presses` (this one included) and
  the settable `event.amount` (percent this press adds; 0 ignores it, negative pushes the bar back).
- `ManholeEvents.skillCheck` is **deprecated** since 1.2.0: still registered so 1.1 scripts load, but it never fires.
  When a script registers it, the server log and the KubeJS console get one warning (checked after every server
  script load, from the `spawnRules` hook).
- `ManholeEvents.travel(event)` has `event.player`, `event.from` (or null), `event.to`, and the settable `event.hunger`
  and `event.timeTicks`. `event.cancel()` stops the trip.
- `ManholeEvents.arrived(event)` has `event.player` and `event.node`. `event.cancel()` skips the built-in ambush roll.

Binding `Manholes`. A `nodeId` is the full UUID or a unique prefix of at least 4 characters, such as the 8-character short id.
- `open(player, nodeId)`, `close(player, nodeId)`, `isOpen(player, nodeId)`
- `nodes(player)` returns a list of nodes; `nodeAt(level, pos)` returns a node or null
- `rename(player, nodeId, name)` sets the player's team alias of a node (what the travel screen, the map icons and the
  arrival message show for that team; plain text, max 32 characters, `''` clears it). Scripts are trusted, so the node
  doesn't have to be in the network. Returns true if the alias changed. A node object's `name` stays the base name.
- `travel(player, nodeId)` is a scripted trip: same fade, no cost, no membership or blocker checks
- `place(level, pos, {name, ruleId, open:false, facing:'north'})` returns a node; `remove(level, pos)`
- `setBlock(level, pos, blockId)` (1.4.0): swaps the cover for `'manholes:hatch'` (or a look id like `'cave'`), keeping
  the node; false if there's no manhole or no cover block matches. `setLook(level, pos, look)` is its deprecated alias
  (warns once, `''` = no-op).
- `setShared(level, pos, bool)` (1.5.0): "share with team" of the home manhole at pos; trusted (no owner check); false
  if there's no home manhole
- `nodes(player)` / `isOpen(player, nodeId)` (1.5.0) include the home manholes visible to the player
- Extras: `get(level, nodeId)`, `all(level)`, `ruleIds()`
- A node object has `id`, `shortId`, `name`, `nameComponent`, `dimension`, `pos`, `x`, `y`, `z`, `ruleId`,
  `structureId`, `home`, `generated`, `look`, `stage`, and (1.5.0) `owner` (uuid string or null), `ownerName` and
  `shared`.

## Commands (op level 2 except `share`, usable as FTB Quests command rewards)
- `/manholes share <node|here> <true|false>` (1.5.0): every player may run it; players must own the home (or be op
  level 2), the console / command blocks may always
- `/manholes open <player> <node|here>` and `/manholes close <player> <node|here>`
- `/manholes list [player]`
- `/manholes tp <player> <node>` (the scripted trip)
- `/manholes name <node> <text>`
- `/manholes setblock <node|here> <block>` (1.4.0; replaces `/manholes look`): a block id (`manholes:grate`,
  suggested) or a look id (`city`); the rest of the line is the id, so no quotes are needed
- `/manholes debug nearby` lists generated manholes within 512 blocks.
- `/manholes regen here` rolls the current chunk's rules again and runs them now.

`<node>` is a UUID, a unique prefix, or `here`, meaning the nearest manhole within 4 blocks of the command source.

## Test datapack (`src/test_datapack/`)
- `manholes_test:test_villages` is a structure rule: villages, chance 1.0, `name_from_structure`, `min_distance` 64,
  `"block": "manholes:hatch"`.
- `kubejs/startup_scripts/manholes_test_items.js` (KubeJS run only) registers `zombie_island:crowbar` for the
  optional tag entry test.
- `manholes_test:test_scatter` is a scatter rule: road blocks, 0.02 per chunk.
- The NBT sample `manholes_test:manhole_sample` is a 3x4x3 box: a stone-brick pad with a closed manhole named
  "Sample Sewer" in the middle.
- The KubeJS example script is in `kubejs/server_scripts/`.
- `art/make_sample_nbt.py` regenerates the NBT. `art/make_placeholders.py` makes placeholder textures but never
  overwrites an existing PNG.

## Verification
- `./gradlew build` passes.
- `./gradlew runGameTestServer` runs without KubeJS or FTB Teams on the classpath, with the test datapack copied to
  `run/world/datapacks/manholes_test`. The server starts cleanly, the log shows the two test spawn rules loading (built-ins
  skipped: the run config keeps `naturalSpawn = false`), and all 27 game tests pass (also in the KubeJS run):
  - the spawn rules loaded;
  - an NBT manhole registers with its NBT name;
  - API place, open, close and remove, with the stage as a scoreboard tag and the cover opening visibly;
  - unbreakable versus home;
  - the generation job places a closed, generated manhole named "Village Plains", spacing blocks a second one, and a
    candidate in an ungenerated chunk is queued;
  - the combat blocker stops a request; a scripted trip with the animation puts the player on the start cover during
    the descent (damage blocked), teleports at the midpoint while the session continues through the ascent, and lands
    next to the cover, not inside a block;
  - naming;
  - `prySimple`: the crowbar item (stack 1, durability from the startup config, iron repairs it, gold doesn't), the
    tag (crowbar yes, iron pickaxe no), and simple mode opening the node after `pryTicks` of simulated use packets;
  - `mashSuccess`: custom settings, use packets every 4 ticks and a press every 2 ticks go through insert, lever and
    slide and the node opens; a press during INSERT is refused;
  - `mashDecay`: 4 presses = 40 %, 5 %/tick decay measured, the bar stops at 0 and the session stays in LEVER;
  - `mashRateCap`: 20 presses in one tick -> 12 accepted, 8 rejected; still refused 10 ticks later, accepted again
    after 21 ticks;
  - `mashGiveUp`: a bar that peaked at 20 % (under the 30 % threshold) decays to 0 without slipping; after 40 % it
    slips: session gone, key must be released, node shut, `failDurabilityCost` taken;
  - `rustDeterministic`: `rustFor(uuid)` is stable and uses all 4 levels, the block entity stores it in NBT, an NBT
    edit overrides it, the preset math (normal rust 2: 30 / 45 ticks, 4 % per press; hard 4.5 % / 1 %), home
    manholes are 0;
  - `aliasPriority`: a rename outside the network is refused; the alias wins for its owner only, the base name is
    untouched and a later base rename doesn't beat it; the entry is flagged as an alias; 32-character cap and `§`
    stripped; `''` clears it; removing the node drops its aliases;
  - `landingHeadroom`: open floor ok, a block at head height no, a bottom slab with 2 free blocks ok, a slab with a
    block at +2 no.
  - `coverBlocks` (1.4.0): the six blocks are registered as `ManholeBlock` with the right look and home flag, are valid
    for the block entity type, have items, resolve from their look and block ids, give the look to the block entity,
    are 2/16 tall; axe / pickaxe tags and wood / stone / metal sounds; bad ids don't resolve;
  - `legacyLookConversion` (1.4.0; since 1.5.0 on `city_manhole`, what the old block becomes): an open, east-facing cover in a network, named, with `look:"cave"`
    NBT becomes `manholes:cave_hole` keeping facing, open, node id, name, network and no `look` NBT; the scheduled path
    turns `look:"manholes:hatch"` into `manholes:hatch`; `mypack:drain` doesn't swap;
  - `setBlock` (1.4.0): `/manholes setblock <shortId> manholes:grate`, `setblock here city`, `minecraft:stone` refused,
    `ManholesAPI.setBlock`, bad ids / no cover refused, the deprecated `setLook` swaps and `''` is a no-op, the KubeJS
    `Manholes.setBlock` / `setLook` in the KubeJS run; node id and record kept; rule JSON `look` and `block` pick the
    block and bad ids (`No Way`, `mypack:drain`, `minecraft:stone`) throw. The generation test checks that
    `test_villages` places a `manholes:hatch` block (look in the block entity and record), and `spawnRulesLoaded`
    checks the rule's look (and the builder's `.look('hatch')` in the KubeJS run);
  - `recipeToggle`: the condition codec is registered, both recipes are loaded while the toggle is on, and the condition
    follows the config value;
  - `matchAnyCrowbar`: the test-only item `manholes:gametest_rusty_crowbar` (registered only with
    `-Dmanholes.gametests=true`) isn't in the tag; with `matchAnyCrowbar = false` it doesn't start prying, with `true` it
    does (a real `useItem` call starts a session, which times out). In the KubeJS run `zombie_island:crowbar` exists and
    is in `#manholes:pry_tools` through the optional entry;
  - The prying tests change config values in memory, so each runs in its own batch and restores the defaults.
- With `naturalSpawn = true` the log shows the 2 built-in rules loaded as well.
- `./gradlew runGameTestServerKubeJS` has KubeJS and Rhino dropped in `run-kubejs/mods` and the example script
  installed. The plugin loads; the `spawnRules` builder, `add`, `modify`, `remove` and `getIds` work; the `generate`
  event fires; the script loads with 0 errors (2 startup scripts, 1 server script); and all 27 tests pass. The example script's `pryPhase` and `mash`
  handlers tag the player and `mashSuccess` checks those tags in this run; the script also still registers the
  deprecated `skillCheck` (1.1 compat): it loads, the deprecation warning is logged, and `mashSuccess` checks it never
  fired. The travel test is skipped in that run because KubeJS
  login packets break the mock player.
- 1.5.0 tests (27 in both runs now): `syncCarriesLook` (hatch, grate, cave_hole, city_manhole and a home: the `network_sync` payload, encoded and decoded through its stream codec, carries look `hatch` / `grate` / `cave` / `city` / `home_manhole` per node; FTB Chunks and the travel screen pick `map_icon_<look>.png` from it), `homePrivateShared` (two fake players: placement sets owner, private, not in the
  team network, owner sees it and the other doesn't, entry flags; a non-owner's toggle is refused, the owner's works,
  shared without FTB Teams is still owner only; sneak + use by owner toggles, by the other doesn't; the console
  command, `ManholesAPI.setShared` and (KubeJS run) `Manholes.setShared`; name tag by the other refused, by the owner
  applied; break rule and `anyoneCanBreak`; node removed with the block), `homeMigration` (an old unowned home in a
  player's network gets that owner and becomes private, the block entity adopts it; an unattributable one stays
  unowned and shared and the first user claims it; attribution is only asserted in the plain run, where the mock
  player is online; the KubeJS run's game-test server has no profile cache), `manholeAlias` (block and item alias,
  a palette entry `manholes:manhole` with facing / open, block entity data kept; `nbtManholeRegisters` also checks that
  the sample NBT's `manholes:manhole` loads as `city_manhole`), `ringPlacement` (JSON / builder offset and margin,
  every candidate 6..24 blocks from the box, deterministic, spread; a fake structure start on the test chunk: inside /
  outside / margin, and a job whose candidate is inside places nothing), `blacklist` (default structure, biome and
  dimension entries, no rolls in the nether, a live config change drops a village job), `lidRotationConvention` (the
  renderer's `LidMath.partMatrix` equals vanilla's `FaceBakery.applyElementRotation` maths, copied: axes x/y/z at
  +-45, +-100 and 22.5 degrees about origin (8, 1.6, 17), right-hand rule, translate after rotate).
- 1.6.0 test (28 in both runs): `conditionOverlaySync` (after `setRust(3)` the update tag is exactly `{rust:3}`, the
  update packet carries it, a fresh block entity reads it back through `handleUpdateTag`; a home's update tag says 0;
  the shipped `city` / `grate` / `hatch` / `cave` look files parse every declared overlay, parts in range, levels 1-3
  present, level 0 absent, each overlay model JSON exists on the classpath; `home_manhole` has none; an inline look
  with a part out of range and a level 4 keeps only the valid entry; a look without the field has no overlays).
- **Not verified**, because the client can't run in this environment:
  - the cover renderer (look loading from resources, extra-model registration, baking check, animation, facing
    rotation, lighting, the static fallback, the look sounds), the new popup button colours and the full-UV map icon
    blits, per-look flavour lines;
  - 1.4.0: the new blocks in game (blockstates / item models of `hatch`, `grate`, `cave_hole`, `city_manhole`, the
    cutout parts, particles, creative tab, breaking with `unbreakable = false`), and a real 1.3.0 world being converted;
  - the travel screen (layout, terrain from loaded chunks, FTB Chunks region textures, popup, rename box), the fade
    overlay, the climb animation, hiding the HUD during trips;
  - the pry HUD (sheet layout, 2x scale, flash / shake / danger strips, key cap), the mash key events (GLFW press vs
    repeat), jump / attack / swing suppression, the crowbar first- and third-person pose and pump (the enum extension
    was loaded on the dedicated server without errors, the client side never ran);
  - the FTB Chunks icons at the new sizes and FTB Chunks' internal map API at runtime (compiled against the real
    2101.1.22 jar only);
  - models, textures and sounds in game; hold-to-pry and mashing in real play and zombies being pulled;
  - sharing a network (and aliases) between FTB Teams members (FTB Teams wasn't run at all);
  - the `pried`, `travel` and `arrived` KubeJS events at runtime;
  - the `ManholeEvents.generate` `setPos` re-validation (compiled, not exercised by a test);
  - real village generation in a normal world. `runServer` needs `eula.txt`, which wasn't accepted on your behalf.
  - 1.5.0: the popup owner line / badge / share button, per-look icons (travel screen, popup, FTB Chunks), the FTB
    Chunks zoom scaling (sizes derived from the decompiled 2101.1.22 code, not seen on screen), the condition labels
    in HUD and popup, the held pry tool in the HUD; FTB Teams sharing between two real team members; ring placement
    around real villages; a real 1.4.0 world being loaded.
  - 1.6.0: the condition overlays on screen (registration and baking of the overlay models, cutout drawing, z-fighting
    of the +0.02 px shells, the overlay following the lid animation and facing), the update packet reaching a real
    client after `/data merge` / `setRust` / chunk load, and the `showConditionOverlays` toggle.

## Decisions (judgement calls where the spec said "ask me")
1. **Visual state is per block, network membership is per team.** A cover that's open in the world still needs prying
   for another team, and prying it again doesn't change its look.
2. **FTB team changes don't migrate networks.** The owner is the player's current FTB team id (`Team.getId()` of
   `getTeamForPlayer`). Joining a party gives you the party's network. Your old solo network stays stored and comes back
   if you leave. Stages are handed to members who log in later.
3. **Hold-to-pry uses the client's repeated use packets** (every 4 ticks while the key is held). A session ends if no
   packet arrives for 8 ticks.
4. **A pry tool on a node already in your network opens the travel screen**, like any other item. The spec only named
   empty hand or non-pry items.
5. **Stage ids use the rule id's path only**: rule `ratlab:leah_farm` gives `manholes_opened_leah_farm`, and `/`
   becomes `_`. Two rules with the same path in different namespaces share a stage.
6. **Rolls and spacing:** `max_per_structure > 1` rolls `chance` once per slot, and global spacing still applies to every
   placement, so extra slots need a small rule `min_distance`. Spacing counts only generated manholes, so home and
   scripted ones don't block generation. If spacing or a script blocks the first valid spot, the whole placement is
   skipped rather than moved.
7. **Generation timing:** the hook is `ChunkEvent.Load` with `isNewChunk()`, which NeoForge fires once on the main
   thread when a new chunk becomes FULL. Placement runs at the end of the level tick, 64 jobs per tick, never inside the
   chunk-load callback. Whatever is still queued runs at server stop. `ChunkEvent.Load` was checked in NeoForge's
   `ChunkStatusTasks`; no better hook was found.
8. **Duplicate node ids are replaced.** A node id already registered at another position (a structure template or
   pick-block copy) gets a fresh UUID.
9. **Scripted travel** (`Manholes.travel`, `/manholes tp`) skips cost, membership, blockers and cooldown, and gets no
   built-in ambush. It still uses the same fade.
10. **Cross-dimension cost** uses coordinates scaled by `coordinateScale`, so nether blocks count as 8. Creative players
    pay no hunger or item cost.
11. **Arrival spot:** the four sides of the cover in order (facing, clockwise, counter-clockwise, opposite), at the same
    height or ±1, then the cover itself as a last resort. Fire, lava, magma, cactus, berry bushes and powder snow count
    as unsafe.
12. **Ambush spawns** 6-14 blocks away, preferring spots out of the player's line of sight, only in loaded chunks. It
    uses `MobSpawnType.EVENT` and sets the target.
13. **Travel screen distances** are computed on the client from positions the server sent. The server still validates
    everything.
14. **Built-in rules** (with `naturalSpawn`) are villages 0.25 and pillager outposts 0.5, both `name_from_structure`,
    plus (1.7.0) three wild scatter rules (cave holes in mountains, grates in swamps, hatches in plains / forests). They sit in `builtin_spawn_rule/`, a separate folder from datapack rules, so datapacks never
    turn them on by accident.
15. **Author name and package:** the package is `it.ratlab.manholes` and the author is "Rat Lab", matching omegafe. No
    RatLab content, lore or names are in the mod.
16. **Game tests ship in the jar** but only register with `-Dmanholes.gametests=true`, which only the Gradle
    game-test runs set.
17. **Sounds** are the mod's own events (`manholes:manhole.pry`, `manhole.open`, `travel.footstep`, `travel.drip`,
    `travel.arrive`), mapped to vanilla sound events in `sounds.json` so a resource pack can replace them.
18. **Texts:** a flavor line is chosen on the client from `manholes.travel.flavor.0..N` (up to 100), so a resource pack
    can add lines.
19. **Names are strings** (plain text, or JSON text if the string starts with `{`, `[` or `"`), so `/data` and NBT edits
    work.
20. **World manholes appear in the Functional Blocks creative tab** (for building structures), next to home manholes.

21. **Mash key**: jump by default (space; the mouse stays free for looking), attack as the alternative in the client
    config. Right-click stays held for the whole attempt (use packets keep the session alive), so the player holds
    right-click and mashes space / left-click.
22. **Mashing replaced skill checks entirely** (1.2.0). SLIDE was kept: a short hold after the mash gives a breather
    and the "cover dragged aside" sound, and it doesn't add a timing element. The give-up rule was kept (simple: the
    bar peaked at 30 %+ and fell back to 0). `ManholeEvents.skillCheck` became a deprecated no-op with a warning rather
    than being removed, so 1.1 scripts don't error on load. `leverTicks` and the skill keys were removed, not
    deprecated.
23. **After "The crowbar slips."** the use key must be released before a new attempt (otherwise holding it would start
    over instantly). A script-cancelled phase does the same.
24. **Rust is stored when first registered**, so a later change to the derivation doesn't re-roll existing covers;
    `rust` in a structure NBT is kept, including for copies whose node id gets replaced.
25. **Climb animation is camera-only**: the server puts the player on the start cover when the descent starts (others
    see him snap there) and on the landing spot at the teleport; the client never moves the player, so a modified
    client can't use the animation to go anywhere. The `arrived` event and ambush moved to the end of the ascent.
    A scripted trip starts from the nearest manhole within 8 blocks, or climbs down on the spot if none.
26. **FTB Chunks terrain in the travel screen** (1.2.0, replaces the 1.1 decision): drawn from FTB Chunks' internal
    `client.map` classes, isolated in `compat/ftbchunks/FTBChunksTerrain` and wrapped in a `Throwable` catch that falls
    back to the loaded-chunks image. Its region textures are FTB Chunks' own; we never free them (FTB Chunks releases
    stale regions itself).
27. **Network sync goes to every online player** after a change, not just the team (changes are rare and the payload is
    small). FTB team changes (joining a party) show on the next change, login or dimension change.
28. **Jar name**: the pack copy is `mods/manholes-1.5.0.jar` (the 1.4.0 jar was deleted), matching the version bump.
29. **Network protocol** version `4` in 1.3.0 (`Entry` gained `look`; `3` was 1.2.0), so an older client can't join a
    newer server by mistake. 1.4.0 keeps protocol `4` (payloads unchanged); the new blocks already make NeoForge's
    registry sync refuse a 1.3.0 client.
30. **Aliases are per owner and plain text**: typed names are never parsed as JSON text (no click events from other
    players' input); `§` and control characters are stripped. The travel screen applies a rename optimistically and
    the next `network_sync` confirms it. Scripts (`Manholes.rename`) skip the membership check.
31. **Map icon sizes**: FTB Chunks' icon API has no absolute size, so the icon draws itself at a fixed size inside FTB
    Chunks' slot (22 px large map, 16 px minimap); the hover area still follows FTB Chunks' sizing.
32. **Generate `setPos` re-validation**: a moved spot must pass the physical checks (in the world, loaded chunk, 2 air,
    full solid floor, no fluid on or next to it) and the spacing again; the rule's `on_blocks` / `avoid_blocks` /
    biome filters are not re-applied (the script chose the spot). An invalid move skips the placement with a warning.

33. **Covers are block-entity rendered** (1.3.0): `ENTITYBLOCK_ANIMATED` instead of an empty blockstate model, so the
    blockstate files didn't change and still give particles and the static fallback. A side effect is that there's no
    block-breaking crack overlay on home manholes (vanilla only draws it for `MODEL` shapes). The BER view distance is
    256 blocks.
34. **Look ids** (1.4.0): the look is fixed per block, so a resource pack can no longer give an existing block a new
    look id; another mod can register its own `ManholeBlock("mypack:drain", false, ...)` (add it to the block entity
    type's valid blocks) and ship `assets/mypack/looks/drain.json`. A block whose look has no usable file renders its
    static blockstate model with a warning. Legacy `look` NBT is converted only for the four built-in looks.
35. **Look sound** plays client-side at the start of every open/close animation, on top of the server's
    `manhole.open` sound on a successful pry.
36. **Flavour look** is the nearest cover within 8 blocks of the player when the fade starts (the start cover), found
    from client block entities, so no payload change was needed. A scripted trip far from any cover uses the generic lines.
37. **`naturalSpawn` defaults to true** for the public release.
38. **Looks became blocks** (1.4.0) because the user asked for separate blocks: players can hold, place (creative) and
    pick-block each kind, and tags / loot / sounds / tools can differ per kind.
39. **Travel button fires on release** (press, then release over it), so the pressed colour is visible.

40. **Home manholes are outside the team network** (1.5.0): ownership replaced membership for owned homes, so leaving a
    team can't take a home with it, and sharing is a live check against the owner's current team. Stages are not
    given for homes any more (they were per node id and only meant for world covers).
41. **Unowned homes** count as shared and can be claimed by the first user (the spec's "unowned and shared"), and are
    breakable by anyone (there's no owner to protect).
42. **Share command for everyone**: `/manholes` no longer requires op at the root; every other sub-command still
    does. `share` checks ownership itself.
43. **FTB Chunks icon sizes** (1.5.0): `getIconScale` 4 on the large map (16 px at 1:1, FTB's floor 6 px zoomed out)
    and our `draw` clamps FTB's already-scaled `w`/`h` to 6..24 px; minimap fixed 12 px (8 px on the rim).
    `isIconOnEdge` stays false, so icons are centred and nothing grows at the border.
44. **Pry HUD tool**: the stack in the session's hand (`PryStatePayload.offHand`) is drawn with
    `GuiGraphics.renderItem` (16 px in the 20 px cell, scaled with the HUD); the sheet's crowbar glyph is only the
    fallback if rendering throws; nothing for an empty hand.
45. **Lid rotation**: the renderer already used `Axis.XP/YP/ZP.rotationDegrees` (= `rotationAxis` on the unit axis)
    about the origin, which is vanilla's convention; 1.5.0 moved the maths into `client/cover/LidMath` (JOML only, no
    angle limit) so a game test can compare it with vanilla's formula. Clipping lids are a look-data matter (hinge
    origin / angle in the look JSON).

## Payloads (protocol `5` since 1.5.0)
- To client: `open_travel_screen`, `fade`, `travel_anim` (descent / ascent), `network_sync`, `pry_state` (every tick
  of a phased attempt; 1.5.0 adds the cover's `look` and `offHand`). `Entry` (1.5.0) adds `home`, `ownerName`, `mine`
  (the viewer owns it) and `shared` (effective).
- To server: `travel_request`, `mash_press`, `rename_node`, `share_home` (1.5.0; node + wanted value, owner / op
  checked on the server).
- Removed in 1.2.0: `skill_check`, `skill_press`.

## Known gaps
- Nothing client-side has been tested in a real game: the cover renderer and looks, the travel screen, terrain, popup and rename, the fade, the climb
  animation, hiding the HUD, the pry HUD and mash key, the crowbar pose, the FTB Chunks icons and terrain, the models,
  textures and sounds.
- FTB Teams integration compiled against the real jar but was not run.
- The `pried`, `travel` and `arrived` KubeJS events were not run.
- Other players don't see the crowbar pry pose (only the local player's pry state is known client-side).
- There's no advancement integration, as the spec intends.
- 1.7.0, not play-tested: the wide outline / collision in a real client (diagonal corners of the overhang are skipped by
  vanilla's collision scan, like any large shape), the translucent overlay sorting, the wild rules' real frequency.
