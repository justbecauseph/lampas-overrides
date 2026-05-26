# lampas-overrides

A small Gradle-based Java project containing overrides and patches for a Minecraft client modding workflow. This repository includes source code, generated artifacts, and a number of helper outputs produced by the project build and decompilation/patching pipeline.

## Quick overview

- Language: Java
- Build system: Gradle (wrapper included)
- Typical JDK: 17+ (use the same JDK used by your Minecraft toolchain)

## Repository layout (important folders)

- `src/main/java/` — primary project source files (package: `town.lampas.overrides`)
- `src/main/resources/` — resources and mod metadata (`META-INF/neoforge.mods.toml`)
- `build/` — Gradle build outputs
- `jars/` — cached and pipeline JARs used for injection/patching
  - `jars/libs/` contains built library JARs (e.g. `lampas_overrides-1.0.0.jar`)
  - `jars/neoForm/` and `jars/neoFormJoined.../steps/` contain toolchain outputs and patch steps
- `generated/` — generated sources and headers from annotation processors or decompilers
- `runs/` — configured run profiles (client/server/test)
- `resources/main/META-INF` — mod metadata used by packing

## Prerequisites

- Java JDK 17 or later installed and available in `JAVA_HOME` or PATH
- No global Gradle install required; the project includes the Gradle wrapper (`gradlew`/`gradlew.bat`)
- (Optional) An IDE (IntelliJ IDEA, Eclipse) configured to use the project JDK

## Build

From the repository root (using the included Gradle wrapper):

```bash
# Run a clean build (Linux/macOS/WSL/Git Bash/Cygwin)
./gradlew clean build

# On plain Windows CMD/PowerShell use:
# .\gradlew.bat clean build
```

Build artifacts will be under `build/` and `jars/libs/`. The main library artifact is usually `lampas_overrides-1.0.0.jar`.

## Run (development)

The repo contains run configurations under `runs/`. If you use an IDE, import the Gradle project and use the generated run targets. From the command line, Gradle run tasks (if configured) can be executed with:

```bash
./gradlew runClient
# or a custom run task defined by the project
```

If specific `run` tasks aren't defined, run configurations are usually implemented via the IDE's Gradle integration or custom tasks in `build.gradle`.

## Tests

If tests are available, run them with:

```bash
./gradlew test
```

Test JVM args are stored in `runs/junit/`.

## Patches, decompilation and pipeline artifacts

This repo appears to be part of a modding toolchain (neoForm / neoforge). Useful locations:

- `jars/neoForm/*/patches/` — patch outputs and rejected hunks (useful when re-applying fixes)
- `jars/neoForm/*/steps/` — step-by-step outputs from the toolchain (applyForgesAccessTransformer, inject, recompile, etc.)
- `generated/` — generated sources and headers after decompilation or annotation processing

When making changes that interact with the patch pipeline, keep a copy of original pipeline outputs to help diagnose rejects in `patches/*/rejects.zip`.

## Common developer workflows

- Import into IntelliJ IDEA with "Open or Import" -> select `build.gradle` and use the Gradle tool window
- Use the Gradle wrapper for consistent builds: `./gradlew assemble` or `./gradlew build`
- When editing mapping or patch files, re-run the relevant pipeline steps (the repo includes many pre-created step outputs in `jars/neoForm.../steps/` to reference)

## Formatting, linting and checks

Add or enable `spotless`, `checkstyle`, or other Gradle plugins in `build.gradle` if you want automatic formatting or linting. This repository doesn't yet document a specific linter in the root.

## Troubleshooting

- Build fails with JDK compatibility errors: ensure `JAVA_HOME` points to JDK 17+ and your IDE is configured to use the same JDK
- Missing Gradle tasks or run configurations: import the Gradle project into your IDE to generate run targets
- Patching rejects: inspect `jars/neoForm/*/patches/*/rejects.zip` and the console logs in `jars/neoForm/*/steps/*/console.log` for details

## Contributing

- Make a branch for each change (feature or fix)
- Keep changes small and focused; include a short description of why a patch is needed (especially for patch pipeline edits)

## License

No license file found in the repository. Add a `LICENSE` file at the repo root if you intend to publish under a particular license.

---

If you'd like, I can: add a short `CONTRIBUTING.md`, generate a `LICENSE` file, or create an abbreviated quick-start for running the client via a specific Gradle run task (if you tell me which task to use).
