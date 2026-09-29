# Manhole Travel

NeoForge 1.21.1 mod: pry open rusted manhole covers (the noise draws the undead) and travel through your team's sewer
network. The mod is data-driven through spawn rules, looks, tags, configs, commands and KubeJS. Only NeoForge is
required; KubeJS, FTB Teams and FTB Chunks integrations are optional.

- Player and pack-maker documentation: [CURSEFORGE.md](CURSEFORGE.md)
- Technical notes: [SUMMARY.md](SUMMARY.md)

## Build
```
./gradlew build                     # build/libs/manholes-<version>.jar (Java 21 toolchain)
./gradlew runGameTestServer         # game tests without optional mods
./gradlew runGameTestServerKubeJS   # game tests with KubeJS + Rhino in run-kubejs/mods
```

## License
MIT, see [LICENSE](LICENSE).
