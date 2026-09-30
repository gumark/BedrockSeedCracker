# BedrockCracker (gumark fork)

> An updated fork of [MiranCZ's BedrockSeedCracker](https://github.com/MiranCZ/BedrockSeedCracker), ported to modern Minecraft and tuned for speed.

[![Download](https://img.shields.io/github/v/release/gumark/BedrockSeedCracker?label=Download&sort=semver&logo=github&logoColor=white&color=2ea44f)](https://github.com/gumark/BedrockSeedCracker/releases/latest)
[![Build](https://img.shields.io/github/actions/workflow/status/gumark/BedrockSeedCracker/build.yml?branch=master&label=Build)](https://github.com/gumark/BedrockSeedCracker/actions/workflows/build.yml)

A relatively small Fabric mod that finds the **seed of the world you are playing in** by analyzing bedrock patterns — no operator permissions, no cheats, works even on vanilla servers. Just uses the bedrock block; **Java Edition only**.

---

## Requirements

| | |
|---|---|
| Minecraft | **26.3** |
| Fabric Loader | **0.19.5+** |
| Fabric API | **0.161.0+26.3** |
| Java | 25+ (for building; playing just needs a matching launcher) |
| OpenCL GPU | *optional* — the mod uses it when present and falls back to a multithreaded CPU cracker otherwise |

## Usage

1. Install the mod with Fabric Loader + Fabric API.
2. In your world, visit **both the overworld and the nether**. The mod silently collects bedrock patterns around you (512 pieces for the overworld, 128 floor + 128 roof for the nether — a small render distance is enough). It tells you in chat when everything is collected.
3. Run **`/crackseed`** and wait. Progress is posted to chat with timestamps, percentages and an ETA; the result (or results) is printed when done.

### How long does it take?

Depends on your machine:

- **With an OpenCL-capable GPU** — usually well under a minute. The GPU cracker runs the search as one massively parallel kernel.
- **CPU only** — a few minutes on a modern CPU (measured ~10 minutes on an Intel i3-1005G1 with integrated graphics only).

If GPU initialization fails at runtime (no OpenCL runtime, driver issues, unsupported device), the mod automatically falls back to the CPU cracker instead of crashing.

## What's changed in this fork

Compared to the upstream repository:

- **Ported to Minecraft 26.3** (Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, Java 25).
- **Fixed the `/crackseed` GPU crash** — the OpenCL kernel now uses bounds-checked result writes, and overflowing batches are retried with the exact required capacity instead of running out of bounds.
- **CPU cracker optimizations** — per-thread padded progress counters, batched result flushing, live chat progress with elapsed time and ETA.
- **GPU kernel rewrite** — the 12-bit suffix search is a depth-first search using only scalar registers (no dynamically indexed queue arrays), plus a grid-stride loop so kernels queue far less work-item scheduling overhead. Verified against a reference implementation.
- **Timestamped chat messages** and clearer phase output.

See the [releases](https://github.com/gumark/BedrockSeedCracker/releases) for downloads.

## How does it work?

All bedrock patterns in versions 1.18+ depend on the world seed in some capacity.

Even though the world seed is a 64-bit number, the nether only uses the **48 bottom bits** of it, and with a much simpler random number generator (a plain 48-bit LCG) that is easier to crack.

So the mod first cracks the **structure seed** (the 48 bottom bits of the world seed) from the nether bedrock roof and floor layers. Then it goes through all 2^16 combinations of the 16 upper bits and checks them against the overworld bedrock — hopefully resulting in the exact world seed.

## Using BedrockCracker in your own mod

You can depend on this mod through [jitpack](https://jitpack.io/):

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    modImplementation (include('com.github.gumark:BedrockSeedCracker:master-SNAPSHOT'))
}
```

Then implement the `BedrockCrackerController` interface:

```java
public class Controller implements BedrockCrackerController {

    @Override
    public void setup(BedrockCrackerSettings settings) {
        // you can change settings of BedrockCracker here
    }

    @Override
    public void seedCrackedEvent(long worldSeed) {
        // called when the world seed is cracked
    }

    @Override
    public int getPriority() {
        // optional, uses 100 if not overridden
        return 150;
    }
}
```

And register your controller in your `fabric.mod.json`:

```json
{
  "entrypoints": {
    "bedrockcracker": [
      "path.to.controller.Controller"
    ]
  }
}
```

Available `BedrockCrackerSettings`:

| Setting | Default | Meaning |
|---|---|---|
| `crackStartType` | `COMMAND` | start cracking via `/crackseed` (`COMMAND`) or automatically once enough bedrock is collected (`AUTO`) |
| `logProgress` | `true` | send progress messages to chat |
| `allowGpuUse` | `true` | use the OpenCL GPU cracker when available (falls back to CPU when disabled or unavailable) |

## Building from source

Requires JDK 25+:

```bash
./gradlew build
```

The mod jar lands in `build/libs/BedrockCracker-<version>.jar`.

## Credits

- Original mod by **[MiranCZ](https://github.com/MiranCZ/BedrockSeedCracker)** — a Java port of [19MisterX98's *Nether_Bedrock_Cracker*](https://github.com/19MisterX98/Nether_Bedrock_Cracker) (Rust) with world-seed cracking added on top.
- This fork maintained by **gumark**.

## License

[MIT](LICENSE) — © 2024 MiranCZ
