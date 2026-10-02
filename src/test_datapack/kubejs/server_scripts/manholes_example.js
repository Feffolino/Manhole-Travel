// Manhole Travel - example server script. Uses every ManholeEvents event and every Manholes binding method.
// Copy to kubejs/server_scripts/. Rhino notes: no `const` inside loops, and never call .equals on a level.

// ---------------------------------------------------------------- spawn rules (server start and /reload)
ManholeEvents.spawnRules(event => {
  // Builder: a structure rule. Ids without a namespace get "kubejs:".
  event.structure('example_village_sewer')
    .structures('#minecraft:village')
    .exclude('minecraft:village_snowy')
    .chance(0.3)
    .surfaceOnly(true)
    .avoidBlocks('#minecraft:leaves')
    .offset(6, 24) // 1.5.0: the cover goes on a ring 6..24 blocks outside the structure's bounding box
    .margin(2)     // and keeps 2 blocks of clearance from every structure's bounding box
    .name('Old Village Sewer')
    .block('hatch') // cover block: a look id ('hatch') or a block id ('manholes:hatch'); default manholes:city_manhole

  // Builder: a scatter rule, rare manholes on roads (e.g. chunk-generator cities).
  event.scatter('example_roads')
    .onBlocks('#manholes:road_blocks')
    .dimensions('minecraft:overworld')
    .chancePerChunk(0.005)
    .minDistance(384)

  // Same thing from JSON (datapack schema).
  event.add('example_outpost', {
    type: 'structure',
    structures: 'minecraft:pillager_outpost',
    chance: 0.5,
    name_from_structure: true,
    placement: { surface_only: true, margin: 0 }
  })

  // Tweak or drop existing rules (datapack ids are namespace:path).
  event.modify('manholes_test:test_villages', rule => rule.chance(0.5))
  event.remove('manholes_test:test_scatter')

  let ids = event.getIds()
  console.info(`[manholes] ${ids.length} spawn rules: ${ids}`)
})

// ---------------------------------------------------------------- each generated spot, before placing
ManholeEvents.generate(event => {
  // Never put one right at world spawn.
  if (Math.abs(event.pos.x) < 64 && Math.abs(event.pos.z) < 64) {
    event.cancel()
  }
  if (event.structureId == null && event.name == null) {
    event.name = 'Storm Drain'
  }
  // event.setPos(event.pos.above()) would move it; the spot is not re-validated.
  console.info(`[manholes] generating ${event.ruleId} at ${event.pos} in ${event.level.dimension}`)
})

// ---------------------------------------------------------------- prying succeeded (cancellable)
ManholeEvents.pried(event => {
  let player = event.player
  // Example gate: hand-placed nodes of other teams need a stage first.
  if (event.node.ruleId == null && !event.node.home && !player.stages.has('sewer_access')) {
    player.tell('The bolts are welded. Maybe later.')
    event.cancel()
  }
  console.info(`[manholes] ${player.username} (${event.teamName}) opened ${event.node.name}, stage ${event.node.stage}`)
})

// ---------------------------------------------------------------- phased prying: a phase starts (cancellable)
ManholeEvents.pryPhase(event => {
  // event.phase is 'insert', 'lever' or 'slide'; event.rust is 0..3. event.cancel() aborts the attempt.
  event.player.addTag(`manholes_example_phase_${event.phase}`)
  console.info(`[manholes] ${event.player.username} ${event.phase} on ${event.node.name} (rust ${event.rust})`)
})

// ---------------------------------------------------------------- phased prying: a press of the mash key (LEVER)
ManholeEvents.mash(event => {
  // event.progress = bar before this press (0..100), event.presses = accepted presses so far (this one included),
  // event.amount = percent this press adds (settable; 0 ignores the press).
  if (event.presses == 1) {
    event.player.addTag('manholes_example_mash')
  }
  // Example: with the stage 'steady_hands' every press counts 50% more.
  if (event.player.stages.has('steady_hands')) {
    event.amount = event.amount * 1.5
  }
})

// ManholeEvents.skillCheck(...) from 1.1 still loads but never fires (a warning is logged): use ManholeEvents.mash.
ManholeEvents.skillCheck(event => {
  event.player.addTag('manholes_example_skill_never')
})

// ---------------------------------------------------------------- a trip was requested (cancellable, editable costs)
ManholeEvents.travel(event => {
  // Night trips are twice as tiring.
  if (event.player.level.isNight()) {
    event.hunger = event.hunger * 2
  }
  // Short hops don't eat the day.
  if (event.from != null && event.from.dimension == event.to.dimension) {
    let dx = event.from.x - event.to.x
    let dz = event.from.z - event.to.z
    if (dx * dx + dz * dz < 500 * 500) {
      event.timeTicks = 0
    }
  }
})

// ---------------------------------------------------------------- arrived (cancel = skip the built-in ambush)
ManholeEvents.arrived(event => {
  event.player.tell(`You climb out at ${event.node.name}.`)
  if (Math.random() < 0.05) {
    event.player.tell('Something followed you...')
    event.server.runCommandSilent(`execute at ${event.player.username} run summon minecraft:zombie ~3 ~ ~3`)
    event.cancel()
  }
})

// ---------------------------------------------------------------- the Manholes binding
ServerEvents.commandRegistry(event => {
  let Commands = event.commands
  event.register(Commands.literal('manholes_example').requires(s => s.hasPermission(2))
    .executes(ctx => {
      let player = ctx.source.player
      let level = player.level
      let pos = player.blockPosition().offset(2, 0, 0)

      // place a real network manhole, then find it again
      let node = Manholes.place(level, pos, { name: 'Script Sewer', ruleId: 'kubejs:script_sewer', open: false })
      if (node == null) return 0
      let same = Manholes.nodeAt(level, pos)
      player.tell(`Placed ${node.name} [${node.shortId}] rule ${node.ruleId}, found again: ${same != null}`)

      // swap the cover block (keeps node, name, facing, open): a block id or a look id
      Manholes.setBlock(level, pos, 'manholes:grate')
      player.tell(`look: ${Manholes.nodeAt(level, pos).look}`)

      // 1.5.0: a home manhole is personal; share it with the owner's team (scripts skip the owner check)
      Manholes.setBlock(level, pos, 'manholes:home_manhole')
      Manholes.setShared(level, pos, true)
      let home = Manholes.nodeAt(level, pos)
      player.tell(`home owner '${home.ownerName}', shared ${home.shared}`)
      Manholes.setBlock(level, pos, 'manholes:grate')

      // network membership
      Manholes.open(player, node.id)
      player.tell(`open? ${Manholes.isOpen(player, node.id)}`)
      let mine = Manholes.nodes(player)
      for (let i = 0; i < mine.length; i++) {
        let n = mine[i]
        player.tell(` - ${n.name} at ${n.x} ${n.y} ${n.z} (${n.dimension})`)
      }

      // scripted trip (same fade, no cost) to the first other node, if any
      if (mine.length > 1) {
        Manholes.travel(player, mine[0].id)
      }

      // clean up: close and remove
      Manholes.close(player, node.id)
      Manholes.remove(level, pos)
      return 1
    }))
})
