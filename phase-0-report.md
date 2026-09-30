# Phase 0 report

## Result

Research and Phase 0 implementation are complete. **Gradle build passed, exit code 0.** Stopped before Phase 1. Client loading and in-game appearance remain untested, for the requested user `runClient` acceptance check.

The official NeoForge 1.21.1 MDK at commit `7819b902a351b03fe71db00754d103b5a31c4ebf` still pins all the requested versions: NeoForge 21.1.252, ModDevGradle 2.0.148, Gradle 9.2.1, Java 21, Parchment 2024.11.17.

## Files created

All paths below are relative to the requested project directory, where the editable project remains.

| Files | Purpose |
| --- | --- |
| `build.gradle` | Official MDK build configuration and Java 21 toolchain |
| `settings.gradle` | Official plugin resolution plus project name `worldeater` |
| `gradle.properties` | Version pins, mod ID/name/group/version |
| `gradlew`, `gradlew.bat` | Official Gradle launchers |
| `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` | Official Gradle 9.2.1 wrapper |
| `.gitignore`, `.gitattributes` | Template conventions; ignores local work/output tooling |
| `TEMPLATE_LICENSE.txt` | Retained template license |
| `src/main/java/com/worldeater/WorldEater.java` | Deferred registration of one item and one creative tab |
| `src/main/templates/META-INF/neoforge.mods.toml` | Expanded mod metadata and dependencies |
| `src/main/resources/assets/worldeater/lang/en_us.json` | Item and creative-tab names |
| `src/main/resources/assets/worldeater/models/item/test_item.json` | Replaceable vanilla generated item model |
| `src/main/resources/assets/worldeater/textures/item/test_item.png` | Script-generated transparent 16x16 placeholder |
| `scripts/generate-assets.ps1` | Reproducible texture/model generation |
| `scripts/dev.ps1` | Local Java 21/cache/socket setup for wrapper commands |
| `docs/research.md` | Short versioned API research and uncertainties |
| `docs/phase-0-report.md`, `README.md` | Phase report and build/launch/acceptance instructions |

Generated `build/libs/worldeater-0.1.0.jar`; delivery copies, source archive and build logs are in `outputs/`. Local template checkout, exact API sources, portable JDK and Gradle cache are under `work/` and are excluded from the source archive. A short socket temp directory is also used under the user's `Documents/Codex/work/worldeater-tmp`.

## Verification

- Full initial Gradle build compiled the Minecraft/NeoForge dependency and mod: **BUILD SUCCESSFUL in 2m 38s**.
- Final helper verification: `./scripts/dev.ps1 build --offline --no-daemon`: **BUILD SUCCESSFUL in 7s**, process exit 0.
- Inspected the built JAR: mod class, expanded TOML, language JSON, item model and PNG are present. Parsed both asset JSON files and visually checked the PNG.
- No automated game tests exist in this phase; Gradle correctly reports `test NO-SOURCE`. No client or dedicated server was launched.

## What to test in game

From the project directory run `./scripts/dev.ps1 runClient --no-daemon`. This selects the downloaded Java 21 JDK. Standard `./gradlew runClient` (Windows: `./gradlew.bat runClient`) is also available once Java 21 and working socket settings are configured.

1. Confirm **World Eater 0.1.0** appears in Mods.
2. Create a creative test world and open **World Eater** in the creative inventory (page through tabs if needed).
3. Confirm exactly one **World Eater Test Item** appears with its purple-ring/dark-center texture.
4. Hold it, drop it and pick it up. With commands enabled, test `/give @s worldeater:test_item`.

## Limitations and uncertainty

The test item is intentionally inert. No recipes, Mass, weapons, entities, config or dimensions are implemented yet. No mixins or client gameplay logic are used. Game labels are in the language file; loader display-name metadata uses `mod_name` in Gradle, as required by the MDK.

Compilation does not establish that Minecraft loads or renders this item; the above client check is still required. Research verified several future APIs against exact 1.21.1/21.1.252 sources, but future runtime behavior is not tested. Lighting suppression/repair, sky appearance, crash recovery, bounded chunk generation and world-destruction performance need the targeted verification described in the research notes.

The machine initially exposed Java 8. A checksum-verified portable Temurin Java 21 JDK was downloaded locally. Network-restricted dependency fetching, Windows socket-path failures and an intermittent Gradle daemon registry write error were resolved for the final build using network-enabled setup, a short socket temp directory and `--no-daemon`. The initial full build also reported upstream Gradle deprecation warnings for future Gradle 10; the requested Gradle 9.2.1 build passed.

No major design changes were made. Await the user's Phase 0 in-game result before Phase 1.
