// Test-only startup script (gameTestServerKubeJS): a foreign crowbar with the id the default
// #manholes:pry_tools tag lists as an optional entry. Not part of the mod.
StartupEvents.registry('item', event => {
  event.create('zombie_island:crowbar').maxStackSize(1)
})
