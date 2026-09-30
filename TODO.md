# TODO

- [ ] **Test the client side in a real game** (nothing client-side has run yet): the animated cover renderer and the look
  files (`manhole`, `home_manhole`, then `hatch`, `grate`, `cave`, `city`), look sounds, `animateCovers = false`, the
  travel popup buttons (normal / hover / pressed / disabled), the map icon with a 16x16 texture, per-look flavour lines,
  the Italian translation.
- [ ] 1.7.2: check the chunk-meshed covers in game (open / closed / animating, every look, rust overlays, facings,
  Sodium + Iris + Omega Flashlight: covers lit like the ground, no blink at animation start / end).
- [ ] CurseForge page: GIFs / screenshots, a real `displayURL` in `neoforge.mods.toml` (placeholder now), and
  `src/main/resources/manholes_logo.png` (referenced by `logoFile`; the art side provides it).
- [ ] Optional: per-look sounds for prying phases and a per-look HUD name (the HUD phase text is generic for now).
- [ ] Optional: let `ManholeEvents.generate` change the look of a spot (`event.look`).

Done in 1.3.0:
- [x] Datapack-driven looks: block entity `look` (NBT, command, KubeJS `Manholes.setLook`, spawn rule `look` /
  `.look()`), an animated block-entity renderer from `assets/<ns>/looks/*.json`, per-look lang names and flavour lines.
- [x] Publishing: `it_it`, default recipes behind `recipes.enableDefaultRecipes` (NeoForge condition),
  `naturalSpawn = true` by default, `CURSEFORGE.md`, `README.md`, mods.toml metadata.
- [x] Every crowbar works: optional tag entries + `prying.matchAnyCrowbar`.
- [x] Travel popup button colours.
- [x] Map icon drawn at any square texture size.

Done in 1.2.0:
- [x] Hide every UI element while travelling: `client/TravelUi` sets `hideGui` from the start of the descent / fade to
  the end of the ascent / fade-in and restores the player's own value at the end, on cancel and on disconnect. FTB
  Chunks' minimap checks `hideGui` itself. The hand is hidden too.
