# Manhole Travel

**Pry open rusted manhole covers and travel through your team's sewer network.**
NeoForge 1.21.1. Only NeoForge is required; KubeJS, FTB Teams and FTB Chunks are optional.

Manhole covers turn up around villages and outposts (or wherever a datapack puts them). They're rusted shut. Wedge a crowbar
in, mash the lever while the noise draws every zombie around, drag the cover aside, and the manhole becomes a node of
your sewer network. From then on, right-click any opened manhole to open the map and travel to any other one.

## Features
- **Prying minigame**: INSERT (hold), LEVER (mash a key against a decaying bar), SLIDE (hold). The noise alerts the
  undead. Each cover has a condition level (0 to 3: rust on iron, swollen wood on hatches, rubble on cave holes), and
  worse covers take longer. If you give up halfway, the crowbar
  slips loudly. There are four difficulty presets, including the 1.0-style "simple" hold.
- **Fast travel**: a full-screen map with the real terrain (FTB Chunks' map if installed, otherwise the chunks around
  you), node icons, a popup with details, per-team renaming, and a climb-down / climb-up camera animation with a fade
  and flavour text.
- **Team networks**: the network belongs to your FTB team when FTB Teams is installed, otherwise to you.
- **Animated covers**: the lid slides or swings aside when a cover opens. Every kind of cover is its own block:
  `manholes:hatch` (Wooden Hatch), `manholes:grate` (Drain Grate), `manholes:cave_hole` (Cave Hole),
  `manholes:city_manhole` (City Manhole, the default) and the player-placed `manholes:home_manhole`. Spawn rules, a
  command or KubeJS pick the block. Resource packs can restyle each look and its map icon.
- **Covers around structures**: generated covers sit in a ring 6-24 blocks outside a structure (configurable per
  rule), never inside any structure. A config blacklist keeps them away from mineshafts, strongholds, ancient cities,
  trial chambers, ocean structures, oceans, rivers, the Nether and the End.
- **Covers in the wild** (1.7.0): cave holes in the mountains, drain grates in swamps and wooden hatches in plains
  and forests, rare (one per few hundred chunks) and at least 256 blocks apart.
- **Solid covers** (1.7.0): the hitbox matches the whole cover (the hatch stays one block). Water and lava can't wash
  covers away, pistons can't push them, and they're tagged so moving mods leave them alone
  (`#c:relocation_not_supported`, Mekanism's cardboard box, Create contraptions).
- **Home manhole**: a craftable, personal travel point. It belongs to whoever places it and is private: only you see
  it and travel to it. Turn on "Share with team" (in its map popup, by sneak + right-click, or with
  `/manholes share`) and your FTB team mates can use it too. Only the owner breaks it or renames it.
- **Costs and dangers** (all configurable): hunger and time per km, an optional item cost, a combat blocker, cooldowns
  and ambushes on arrival.
- **Server authoritative**: every request is validated on the server.

## Recipes (vanilla items, can be turned off)
- **Crowbar**: 3 iron ingots in a diagonal, plus 1 red dye next to the top one.
- **Home manhole**: 4 iron ingots in the corners, 4 stone bricks on the sides, an iron trapdoor in the centre.

Both use the NeoForge load condition `manholes:default_recipes_enabled` (config `recipes.enableDefaultRecipes`).
Datapacks and KubeJS can still replace or remove them by id (`manholes:crowbar`, `manholes:home_manhole`).

## Config
`config/manholes-common.toml`, main keys:

| key | default | |
|---|---|---|
| `generation.naturalSpawn` | true | built-in spawn rules: villages 25 %, pillager outposts 50 %, wild covers (1.7.0) |
| `generation.disableAllGeneration` | false | kill switch for every rule |
| `generation.minDistance` | 192 | spacing between generated manholes |
| `generation.structureBlacklist` | `#minecraft:mineshaft`, `minecraft:stronghold`, `minecraft:ancient_city`, `minecraft:trial_chambers`, `minecraft:buried_treasure`, `minecraft:monument`, `#minecraft:ocean_ruin`, `#minecraft:shipwreck` | ids or `#tags`; no rule spawns around them |
| `generation.biomeBlacklist` | `#minecraft:is_ocean`, `#minecraft:is_river` | ids or `#tags`; every rule, scatter included |
| `generation.dimensionBlacklist` | `minecraft:the_nether`, `minecraft:the_end` | dimension ids; every rule |
| `home.anyoneCanBreak` | false | true: anyone can break home manholes in survival |
| `prying.difficulty` | normal | `simple`, `normal`, `hard`, `custom` |
| `prying.matchAnyCrowbar` | true | any item whose id contains `crowbar` pries |
| `prying.noiseRadius` | 24 | |
| `prying.rustEnabled` | true | |
| `recipes.enableDefaultRecipes` | true | |
| `travel.allowCrossDimension` | false | |
| `travel.hungerCostPerKm` / `timeCostPerKm` | 2.0 / 1000 | |
| `travel.extraItemCost` | "" | item id or `#tag` |
| `travel.ambushChance` | 0.1 | mobs from `#manholes:ambush_mobs` (empty by default) |
| `travel.animationEnabled` | true | climb animation |

`config/manholes-client.toml`: `mashKey` (`JUMP` / `ATTACK`), `animateCovers` (true), `showConditionOverlays` (true).
`config/manholes-startup.toml`: `crowbar.crowbarDurability` (250, restart needed).

## Tags
- `#manholes:pry_tools` (item): `manholes:crowbar`, plus optional `#c:tools/crowbars`, `#c:crowbars`,
  `#forge:tools/crowbars` and `zombie_island:crowbar`.
- `#manholes:attracted_by_noise` (entity type): `#minecraft:undead`.
- `#manholes:ambush_mobs` (entity type): empty, which means no ambush.
- `#manholes:road_blocks` (block): for scatter rules on roads.

## Datapack spawn rules
Put them in `data/<namespace>/manholes/spawn_rule/<name>.json`. KubeJS's `kubejs/data/` folder works too.

```json
{
  "type": "structure",
  "structures": "#minecraft:village",
  "exclude": ["minecraft:village_snowy"],
  "chance": 0.35,
  "max_per_structure": 1,
  "offset": [6, 24],
  "placement": { "surface_only": true, "avoid_blocks": "#minecraft:leaves", "margin": 2 },
  "name_from_structure": true,
  "block": "manholes:hatch",
  "min_distance": 192,
  "dimensions": ["minecraft:overworld"]
}
```

```json
{ "type": "scatter", "chance_per_chunk": 0.01, "on_blocks": "#manholes:road_blocks",
  "biomes": "#minecraft:is_overworld", "min_distance": 256, "name": "Storm Drain", "block": "manholes:grate" }
```

`block` (alias `look`) takes a block id (`manholes:cave_hole`) or a look id (`cave`); the default is
`manholes:city_manhole`.

Structure rules place the cover **around** the structure: on a square ring `offset` = `[min, max]` blocks (default
`[6, 24]`) outside its bounding box. `margin` is the clearance the spot keeps from every structure's bounding box (the
rule's own included), so the spot is never inside or right against a structure. Built-in rules: villages place a
`manholes:hatch`, pillager outposts a `manholes:city_manhole`.

Built-in scatter rules (1.7.0): `manholes:wild_cave_holes` (cave hole, `#minecraft:is_mountain` / `#c:is_mountain`,
stony peaks, gravelly hills, on stone or gravel, 0.004 per chunk), `manholes:wild_drain_grates` (grate, `#c:is_swamp`, on
grass / mud / dirt, 0.003) and `manholes:wild_hatches` (hatch, `#c:is_plains` / `#minecraft:is_forest`, on dirt / grass,
0.0025), all with open sky and `min_distance` 256.

A rule with the same id as a built-in one (`manholes:villages`, `manholes:pillager_outposts`, `manholes:wild_*`)
replaces it.

## Looks (resource packs)
Each cover block has a fixed look: `home_manhole`, `hatch`, `grate`, `cave` (`manholes:cave_hole`) and
`city` (`manholes:city_manhole`). Its animation is `assets/manholes/looks/<look>.json`:

```json
{
  "base_closed": "manholes:block/parts/hatch_base_closed",
  "base_open":   "manholes:block/parts/hatch_base_open",
  "lid_parts": [
    { "model": "manholes:block/parts/hatch_lid_0",
      "open": { "rotate": { "axis": "z", "angle": -100, "origin": [17, 2.25, 8] } } },
    { "model": "manholes:block/parts/hatch_lid_1",
      "open": { "translate": [0, 3.2, 0] } }
  ],
  "duration_ticks": 12,
  "sound_open": "manholes:manhole.slide",
  "sound_close": "manholes:manhole.slide",
  "condition_overlays": [
    { "part": 0, "levels": { "1": "manholes:block/parts/hatch_lid_0_cond_1",
                             "2": "manholes:block/parts/hatch_lid_0_cond_2",
                             "3": "manholes:block/parts/hatch_lid_0_cond_3" } }
  ],
  "base_condition_overlays": {
    "closed": { "1": "manholes:block/parts/hatch_base_closed_cond_1", "2": "...", "3": "..." },
    "open":   { "1": "manholes:block/parts/hatch_base_open_cond_1",   "2": "...", "3": "..." }
  }
}
```

- Units are model pixels (1/16 block), in the north-facing model frame. Lid models are drawn closed.
- `open` is applied rotate first (about `origin`, right-hand rule, exactly like model element rotations), then
  translate. Any angle works (not only +-45). The movement uses ease-in-out.
- `condition_overlays` (optional, 1.6.0) shows the cover's condition (rust / swollen wood / rubble, level 1-3) on the
  block: for level L the model `levels[L]` is drawn over lid part `part` (index into `lid_parts`), moving with it.
  `base_condition_overlays` (optional, 1.7.0) does the same for the static frame: `closed` is drawn with
  `base_closed`, `open` with `base_open`. Overlays use the render type their model declares: cutout by default,
  `"render_type": "minecraft:translucent"` for soft semi-transparent decals. Make it a slightly inflated copy of the lid (+0.02 px) with a transparent decal texture.
  Level 0 or a missing level draws nothing; home manholes are always level 0. A model that fails to load only drops
  that overlay. Players can turn overlays off with `showConditionOverlays = false`.
- Covers are rotated by the block's `facing`. If a look is missing or broken, the cover shows the block's static model
  and the log says why.
- Map icon: `assets/manholes/textures/gui/map_icon_<look>.png` (16x16), falling back to `map_icon.png`.
- Condition label: `manholes.condition.<look>` and `manholes.condition.<look>.0..3` (falls back to the rust wording).
- Lang: `manholes.look.<look>` is the display name. `manholes.travel.flavor.<look>.0..N` are travel lines for trips
  that start at that look; without them the generic `manholes.travel.flavor.N` lines are used.

## KubeJS
```js
ManholeEvents.spawnRules(e => {
  e.structure('my_sewers').structures('#minecraft:village').chance(0.3).offset(6, 24).block('manholes:hatch').name('Old Sewer')
  e.scatter('roads').onBlocks('#manholes:road_blocks').chancePerChunk(0.005).block('grate')
  e.remove('manholes:pillager_outposts')
})
ManholeEvents.pried(e => { /* e.player, e.node, e.team; e.cancel() */ })
ManholeEvents.travel(e => { e.hunger = 0 })
// Manholes.place(level, pos, {name, ruleId, open, facing}), Manholes.setBlock(level, pos, 'manholes:cave_hole'),
// Manholes.open/close/isOpen(player, nodeId), Manholes.nodes(player), Manholes.travel(player, nodeId),
// Manholes.rename(player, nodeId, name), Manholes.remove(level, pos), node.look,
// Manholes.setShared(level, pos, true) (home manholes), node.owner / node.ownerName / node.shared
```
Events: `spawnRules`, `generate`, `pried`, `pryPhase`, `mash`, `travel`, `arrived`. See the example script in the
source repository.

## Commands
Op level 2: `/manholes open|close <player> <node|here>`, `/manholes list [player]`, `/manholes tp <player> <node>`,
`/manholes name <node> <text>`, `/manholes setblock <node|here> <block>` (swaps the cover, keeps the node),
`/manholes debug nearby`, `/manholes regen here`.
Every player: `/manholes share <node|here> <true|false>` on their own home manholes (ops on any).

## NBT
The block entity reads `name` and `rust` (0-3), for example `/data merge block ~ ~ ~ {name:"Old Mine"}`. This works in
NBT structures too. Updating from 1.3.0: a `manholes:manhole` with the old `look` tag (`hatch`, `grate`, `cave`,
`city`) is turned into the matching block when its chunk loads, keeping its node, name, facing and open state.

Updating from 1.4.0: `manholes:manhole` no longer exists. Worlds, structure NBTs and inventories load it as
`manholes:city_manhole` (a registry alias), keeping node, name, rust, facing and open state. Home manholes get the owner
the old save knows (the player whose network held it); the rest become unowned and shared until the first player who
uses one claims it.

## Compatibility
- **FTB Teams**: networks are shared by the team; shared home manholes follow the owner's current team.
- **FTB Chunks**: node icons (per cover type, scaled with the zoom) on the large map and minimap, and its map as the
  terrain of the travel screen.
- **KubeJS**: events, a binding, spawn-rule builders and stages.
- **Mekanism / Create / other movers**: covers are in `#c:relocation_not_supported`, `#mekanism:cardboard_blacklist`
  and `#create:non_movable`. For **Carry On** add `"manholes:*"` to `blacklist.forbiddenTiles` in
  `config/carryon-common.toml`.
- Works without any of them.

## FAQ
- **Can I break a manhole?** World covers (hatch, grate, cave hole, city manhole) are unbreakable while
  `unbreakable = true`. Home manholes break (for their owner, or anyone with `home.anyoneCanBreak`) and drop
  themselves.
- **My modded crowbar doesn't work.** It works if its id contains `crowbar` (`matchAnyCrowbar`). Otherwise add it to
  `#manholes:pry_tools`. Only the mod's own crowbar gets the special prying pose.
- **No manholes spawn.** Check `naturalSpawn`, `disableAllGeneration`, and your rules in the log ("Loaded N manhole
  spawn rules"). Manholes only generate in new chunks.
- **Covers pop open without animating.** Check `animateCovers` in the client config and look for "Manhole look" lines
  in the log.

## For pack makers
- Turn the built-in rules off with `generation.naturalSpawn = false` in `config/manholes-common.toml` (ship it with the
  pack), then add your own `data/<pack>/manholes/spawn_rule/*.json` or KubeJS rules. Override a built-in rule by
  shipping `data/manholes/manholes/builtin_spawn_rule/villages.json` in a datapack. A datapack rule with the same id
  replaces it.
- Turn the default recipes off with `recipes.enableDefaultRecipes = false`, or replace `manholes:crowbar` /
  `manholes:home_manhole` in a datapack or KubeJS.
- Pick the cover block per rule (`"block"`), per structure NBT (place the block itself), or later with
  `/manholes setblock` or `Manholes.setBlock`.
- Stages: opening a node gives `manholes_opened_<rule path>` (KubeJS stages, or a scoreboard tag), which is handy for
  quests.
- Everything the player sees is in lang files (`manholes.*`); flavour lines per look can be added by resource packs.

License: MIT.
