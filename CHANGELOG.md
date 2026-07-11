# Changelog — ProtocolLib 2 (Firepdx fork)

All notable changes made in this fork relative to upstream
[dmulloy2/ProtocolLib](https://github.com/dmulloy2/ProtocolLib) are documented here.
This fork keeps the original GPLv2 license and author credit intact; see `Readme.md` for
attribution details.

## Unreleased

### Fixed
- **Reverted the Java 21 change below.** It broke the build entirely: `compileTestJava` failed
  with 100 errors, all `bad class file ... has wrong version 69.0, should be 65.0`. Root cause:
  `mcVersion = "26.2"` in `build.gradle.kts` depends on `org.spigotmc:spigot:26.2-R0.1-SNAPSHOT`,
  whose NMS classes are Java 25 bytecode. `javac` cannot read class files newer than the JDK
  it's running on, regardless of `sourceCompatibility`/`targetCompatibility` settings - so the
  **compiler itself must run on JDK 25** as long as this project depends on Minecraft 26.2's
  internals. `sourceCompatibility`, `targetCompatibility`, and the Gradle toolchain are back to
  Java 25 in `build.gradle.kts`, and `.github/workflows/build.yml` / `dev-build.yml` runner JDK
  is back to 25.
  - **Important distinction:** requiring JDK 25 to *build* this project is separate from what
    JVM a *deployed server* needs. If you're seeing `UnsupportedClassVersionError` at runtime
    on an actual server (not in CI), that means that server's JVM is older than 25 - a
    deployment-side fix (upgrade that server's Java), not something to "fix" by lowering the
    build target again, since doing so just breaks compilation instead (as this entry shows).
  - The only way to target an older JVM's bytecode at both build and run time is to point
    `mcVersion` at an older Spigot/Paper release (e.g. `"1.21.4"`) instead of `"26.2"` - but
    that requires re-validating every NMS class reference in this codebase against that
    version's actual mappings, since class names/packages differ between Minecraft versions.
    That's a substantially larger change than a version-number edit; treat it as a separate task.

### Previous (incorrect) attempt, kept for context
- **Class file version mismatch causing `UnsupportedClassVersionError` (class file major
  version 69) on Paper 1.21.4 servers.** The previous entry in this changelog pinned the build
  to Java 25 (class file version 69), which only works if the server's actual JVM is 25+. Paper
  1.21.4 officially targets Java 21, so any server not explicitly running a confirmed Java 25
  JVM would fail to load the jar with exactly this error. Reverted `sourceCompatibility`,
  `targetCompatibility`, and the Gradle toolchain to Java 21 in `build.gradle.kts`, and reverted
  `.github/workflows/build.yml` / `dev-build.yml` runner JDK from 25 back to 21 to match.

### Added
- `StructureModifier.readOr(int fieldIndex, T defaultValue)` — version-safe field read with a
  fallback value, shorthand for `optionRead(fieldIndex).orElse(defaultValue)`.
- `PacketContainer.withModification(Consumer<StructureModifier<Object>>)` — fluent chaining
  helper for building/mutating packets in a single expression.
- `PacketContainer.toDebugString()` — human-readable, multi-line dump of a packet's type and
  every readable field value, for use in development logging. Never throws.
- New utility class `com.comphenix.protocol.utility.ViaVersionSupport`:
  - `isViaVersionPresent()`, `isViaBackwardsPresent()`, `isViaRewindPresent()`,
    `isAnyViaPluginPresent()` — reflection-based detection of the Via* plugin family.
  - `getPlayerProtocolVersion(Player)` / `getPlayerProtocolVersion(UUID)` — returns the
    client's actual protocol version as reported by ViaVersion (falls back to the server's own
    protocol version if ViaVersion isn't installed). No compile-time dependency on ViaVersion.
- `CHANGELOG.md` (this file).

### Changed
- Project renamed to "ProtocolLib 2" for this fork (cosmetic only):
  - `settings.gradle` root project name → `ProtocolLib2`
  - `Readme.md` title and fork-attribution notice added
  - Built jar filename → `ProtocolLib2.jar`
  - GitHub Actions workflow titles and artifact names updated to match
  - Added Firepdx as a fork-maintainer entry in the Maven POM `developers` block, alongside
    (not replacing) the original author, Dan Mulloy
  - **Not changed:** `plugin.yml`'s `name: ProtocolLib` field was deliberately left as-is, so
    this fork remains a valid `depend: [ProtocolLib]` target for any other plugin.
- `gradle.properties`: version bumped from `5.5.0-SNAPSHOT` to `5.5.0` (release).
- `build.gradle.kts`: `sourceCompatibility`/`targetCompatibility` explicitly pinned to
  `JavaVersion.VERSION_25` (previously commented out, relying only on the toolchain block).
  Documented in-line why an `UnsupportedClassVersionError` (class file major version 69) occurs
  if the server JVM is older than Java 25.
- `.github/workflows/build.yml` and `.github/workflows/dev-build.yml`: runner JDK bumped from
  21 → 25 to match the Gradle toolchain requirement.
- `.github/workflows/dev-build.yml`: added `permissions: contents: write` at the job level to
  fix a `403: Resource not accessible by integration` error when creating/updating the
  `dev-build` pre-release (GitHub defaults new repos' `GITHUB_TOKEN` to read-only).

### Notes
- Minecraft version support (1.8 through 26.2) required no changes — upstream `MinecraftVersion`
  and `MinecraftProtocolVersion` already covered this full range.
- A real compiled build has not been verified from this environment (no network access to
  Gradle/Maven Central/Spigot/CodeMC repositories here); verify via the `build.yml` /
  `dev-build.yml` GitHub Actions workflows or a local build with JDK 25.
