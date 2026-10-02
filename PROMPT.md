# Port di "Manhole Travel" (manholes) a Forge 1.20.1

## Obiettivo
Portare la mod `manholes` (Manhole Travel **1.7.3**) da **NeoForge 1.21.1** a **Forge 1.20.1**, con le stesse funzionalità, in un progetto nuovo dentro **questa cartella**:
`C:\Users\stefy_zgbvz6k\Desktop\manholes-1.20.1`

Serve per il modpack "Rat Lab", in fase di port a Forge 1.20.1 (istanza CurseForge `RatLab (1)`, Forge **47.4.23**). Il pack su 1.20.1 parte da **mondi nuovi**: i salvataggi 1.21.1 non verranno caricati.

## Sorgente di riferimento: solo lettura
`C:\Users\stefy_zgbvz6k\Desktop\manholes` (git, HEAD `46fdcc3` = 1.7.3). **Non modificarlo**, non fare commit né push lì.
- 75 file Java in `src/main/java/it/ratlab/manholes/`, divisi per area: `api/`, `block/`, `client/` (HUD, schermata viaggio, mappa, `cover/` con renderer, look e overlay), `command/`, `compat/` (ftbchunks, ftbteams, kubejs), `data/`, `gen/` (spawn rule e worldgen), `item/`, `net/`.
- `src/main/resources/`: assets (look JSON, modelli, texture, lang en_us + it_it), data (tag, ricette, loot table, `manholes/spawn_rule`), `META-INF/enumextensions.json`.
- **`SUMMARY.md` è la documentazione completa**: config, tag, JSON, API KubeJS, payload e 45 decisioni di design. Leggilo per primo. Leggi anche `README.md`, `TODO.md`, `CURSEFORGE.md` e `LICENSE` (MIT, "Stefano Manca").
- Test: game test in `src/test*` e `src/test_datapack/` (31 test).

### Texture disegnate a mano dall'autore: non rigenerarle
- `textures/item/crowbar.png`, `textures/gui/map_icon.png`, `textures/gui/map_icon_{city,grate,hatch,cave,home_manhole}.png`
- **Non eseguire** gli script `art/*.py`. In particolare niente `--force` su `make_assets.py` o `make_map_icons.py`.
- Copia gli asset così come sono da `src/main/resources` (non da `build/`).

## Dipendenze opzionali, già in `libs/` (versioni Forge 1.20.1 del pack)
```
kubejs-forge-2001.6.5-build.26.jar      rhino-forge-2001.2.3-build.10.jar
ftb-teams-forge-2001.3.2.jar            ftb-library-forge-2001.2.13.jar
ftb-chunks-forge-2001.3.8.jar           architectury-9.2.14-forge.jar
```
Tutte `compileOnly` (eventualmente anche nel runtime locale per i test). La mod deve funzionare con **zero dipendenze obbligatorie**, come l'originale: le compat stanno in `compat/` e si caricano solo se la mod corrispondente è presente. Puoi usare anche i Maven (maven.latvian.dev, maven.ftb.dev, maven.architectury.dev), ma con **le stesse versioni** dei jar qui sopra.

## Funzionalità da mantenere
Tutto quello che descrive `SUMMARY.md`:
- blocchi cover (`city_manhole`, `hatch`, `grate`, `cave_hole`, `home_manhole`) con un solo tipo di BlockEntity `manholes:manhole`;
- crowbar e tag `#manholes:pry_tools`;
- apertura INSERT → LEVER (mash dei tasti) → SLIDE;
- rete di viaggio per team FTB o per giocatore, con SavedData;
- home manhole personali e condivisibili;
- schermata viaggio con mappa (terreno FTB Chunks se presente, altrimenti i chunk caricati), popup e rinomina;
- look JSON con coperchi animati (BER) e condition overlay (rust);
- spawn rule JSON e ring intorno alle strutture con blacklist;
- scatter rule wild;
- ambush, saltata nelle home manhole e nei chunk claimati;
- comandi `/manholes`;
- API Java (`ManholesAPI`) e KubeJS (eventi + binding);
- tag di compat (relocation, cardboard Mekanism, create non_movable);
- config client / common / startup.

**Coperchi:** il BER li disegna **sempre**. Non reintrodurre la mesh nei chunk a riposo: nella 1.7.2 faceva lampeggiare il coperchio all'apertura ed è stata tolta.

## Cosa si può togliere (motivo: niente salvataggi 1.21.1 su 1.20.1)
- Alias del registry `manholes:manhole` → `city_manhole`.
- Conversione del `look` NBT della 1.3.
- `ManholeData.migrateLegacyHomes`.
- Il metodo deprecato `ManholeEvents.skillCheck` e `setLook`. Se costa poco tenerli come alias, tienili.

Scrivi in `SUMMARY.md` cosa hai tolto.

## Stack di destinazione
- Minecraft **1.20.1**, Forge **47.4.23** (requisito `[47,)`), Java **17** (toolchain).
- Build con **ForgeGradle 6** dal MDK Forge 1.20.1, oppure con **ModDevGradle `legacyforge`**. Scegline uno e spiega perché.
- Mappings: official + Parchment 1.20.1.
- `META-INF/mods.toml`: modId `manholes`, display name "Manhole Travel", versione **1.7.3-1.20.1**, licenza MIT, dipendenze opzionali per kubejs, ftbteams e ftbchunks (`mandatory=false`, side BOTH, ordering AFTER).

## Differenze NeoForge 1.21.1 → Forge 1.20.1 da gestire
| 1.21.1 (originale) | 1.20.1 (da fare) |
|---|---|
| payload `CustomPacketPayload` + `StreamCodec` (`net/*Payload`), protocollo "5" | `SimpleChannel` (`NetworkRegistry.newSimpleChannel`) con encode/decode su `FriendlyByteBuf` e versione di protocollo "5" |
| `META-INF/enumextensions.json` per `HumanoidModel.ArmPose.MANHOLES_PRY` | `HumanoidModel.ArmPose.create("MANHOLES_PRY", twoHanded, transformer)` (IExtensibleEnum) chiamato in `initializeClient` |
| `IClientItemExtensions` | stesso concetto su Forge: `Item#initializeClient(Consumer<IClientItemExtensions>)` |
| Data Components su ItemStack | tag NBT (`getOrCreateTag`) |
| `ModConfig.Type.STARTUP` (`manholes-startup.toml`, durabilità della crowbar) | non esiste: leggi a mano un toml prima della registrazione, oppure usa COMMON e documenta il compromesso |
| condizione di ricetta `manholes:default_recipes_enabled` | `ICondition` + `IConditionSerializer` registrato con `CraftingHelper.register` (Forge 1.20.1) |
| `data/*/recipe/`, `loot_table/`, `tags/item`, `tags/block` | `recipes/`, `loot_tables/`, `tags/items`, `tags/blocks` (al plurale) |
| tag `c:` (`#c:is_swamp`, `#c:ingots/iron`, `#c:tools/crowbars`, …) | in genere `forge:` su 1.20.1 (`#forge:ingots/iron`, `#forge:is_swamp`…). Verifica nel pack quali esistono e, dove serve, tieni entrambi come voci opzionali |
| `ModelEvent.RegisterAdditional` con `ModelResourceLocation` | Forge 1.20.1: `ModelEvent.RegisterAdditional#register(ResourceLocation)` e recupero con `getModel(ResourceLocation)` |
| `SavedData` con `HolderLookup.Provider` | 1.20.1: `SavedData.load(CompoundTag)` / `save(CompoundTag)` con `computeIfAbsent(load, create, name)` |
| `BlockEntity` `saveAdditional/loadAdditional(…, Provider)` | `saveAdditional(CompoundTag)` / `load(CompoundTag)` |
| API KubeJS 7 (2101) | KubeJS 6 (2001): `KubeJSPlugin#registerEvents` con `EventGroup`, `registerBindings(BindingsEvent)`. Controlla `kubejs.plugins.txt`. Verifica le API nel jar in `libs/` |
| API FTB 2101 | API FTB 2001 (stessi concetti, package o firme diverse). Verifica sui jar in `libs/`, soprattutto claim e terreno di FTB Chunks (`FTBChunksTerrain`, `FTBChunksClaims`, icone sulla mappa) |
| `NeoForge.EVENT_BUS` / bus della mod | `MinecraftForge.EVENT_BUS` / `FMLJavaModLoadingContext.get().getModEventBus()` |
| GameTest NeoForge | GameTest Forge (`@GameTestHolder`, `runGameTestServer`) |

## Build
- Sul PC **non ci sono JDK 17 né 21**: in `C:\Program Files\Java` ci sono solo jdk-22, jdk-23 e jdk-25. Usa il toolchain Gradle con il resolver foojay per Java 17.
- ForgeGradle 6 richiede **Gradle 8.x**, non 9. Avvialo con `JAVA_HOME="C:\Program Files\Java\jdk-22"`. Se non parte, chiedi invece di installare un JDK di sistema.
- Risultato atteso: `build/libs/manholes-1.7.3-1.20.1.jar`, senza jar di altre mod dentro.

## Regole
- **Non modificare** `Desktop/manholes` né i jar in `libs/` o nel modpack. Il pack è pubblicato: niente patch ai jar di altre mod.
- **Non copiare il jar** in `C:\Users\stefy_zgbvz6k\curseforge\minecraft\Instances\RatLab (1)\mods` mentre Minecraft è aperto: un jar sostituito a caldo fa crashare il gioco. Controlla che non ci sia un processo `java`/`javaw` con `RatLab (1)` nella riga di comando, e **chiedimi conferma** prima di copiarlo.
- Nella nuova cartella: `git init`, `.gitignore` (Gradle, IDE, `run/`, `run-kubejs/`, `libs/`) e commit a ogni passo funzionante. Non fare push e non creare repository remoti.
- Copia e adatta `README.md`, `SUMMARY.md`, `TODO.md`, `CURSEFORGE.md` e `LICENSE`: target Forge 1.20.1 e versioni delle dipendenze opzionali.

## Ordine di lavoro
1. Setup del progetto e build vuota.
2. Registry: blocchi, BE, item, crowbar, tag e ricette con la condizione.
3. SavedData e rete/nodi, comandi.
4. Networking `SimpleChannel` e tutti i payload.
5. Logica di apertura (mash) e viaggio con fade, landing e ambush.
6. Worldgen: spawn rule, ring intorno alle strutture, blacklist, scatter.
7. Client: HUD, posa della crowbar, BER dei coperchi con look e overlay, schermata viaggio e mappa.
8. Compat: FTB Teams, FTB Chunks (claim, terreno, icone), KubeJS (eventi, binding, plugin).
9. Game test.
10. Documentazione.

Fermati a chiedere solo per le decisioni che spettano all'autore.

## Verifica (prima di dire che è finito)
1. `gradlew build` termina senza errori.
2. `gradlew runGameTestServer`: i game test portati passano. Elenca quelli tolti e spiega perché.
3. Se riesci, `runClient` con FTB Teams/Chunks e KubeJS nel runtime:
   - piazzi una home manhole;
   - la apri con la crowbar (mash);
   - apri la schermata di viaggio, la mappa si vede e viaggi;
   - il coperchio è animato;
   - nella home manhole non scatta l'ambush.
4. In `SUMMARY.md` scrivi cosa hai **verificato davvero** e cosa resta da provare nel modpack. Le parti client (HUD, mash, posa, mappa, icone FTB Chunks) vanno segnate come "da testare in gioco" se non le hai provate.
