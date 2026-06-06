# Performance Optimization Research

Research into Paper, Purpur, Pufferfish, Gale, and Leaf to identify upstreamable performance improvements.

## Current Status

Sugarcane is based on Paper 26.1.2 (Minecraft 1.21.1). It already includes Moonrise (Spottedleaf's chunk system rewrite, Starlight lighting engine, dataconverter rewrite, entity activation range 2.0, incremental saving, optimized hoppers, Alternate Current redstone).

Many optimizations from downstream forks have already been merged upstream into Paper. This doc tracks what's still out there.

---

## Chunk Generation & Loading

### Already in Paper (Moonrise chunk system)
- Starlight lighting engine (rewritten light engine, ~2x faster than vanilla 1.20+)
- Async chunk I/O and loading
- Multi-threaded chunk generation (worker thread pool)
- Priority/urgency system for chunk loads
- Parallel chunk status generation tasks
- Chunk unload delay configurable
- `prevent-moving-into-unloaded-chunks` config
- `delay-chunk-unloads-by` config
- `max-auto-save-chunks-per-tick` config
- Player chunk send/load/generate rate limiting

### Notable Paper PRs/Commits
- **#8177** — Rewrite chunk system (Spottedleaf, merged 2022). Massive overhaul. 2-3x chunk gen speed with enough worker threads.
- **#2308** — Async chunk API/IO/loading (Spottedleaf, merged 2019). Foundation for async chunk ops.
- **219f86e** — Implement chunk unload delay config option (Spottedleaf, June 2025). Adds configurable unload delay.
- **e4eb69b** — Fix async ticket level processing race (Spottedleaf, June 2025). Thread safety fix for Moonrise.
- **6f71be8** — Sync Moonrise config hooks (Spottedleaf, Feb 2026). Configurable unload min count/fraction.

### Leaf-specific
- **Async chunk sending** — Encodes and sends chunk data to players off the main thread. Reduces lag on join/teleport/flying.
- **Async pathfinding** (Petal) — Offloads pathfinding to async threads. Uses thread-local for openSet/neighbors.
- **Async mob spawning** — Avoids sync chunk loads for mob spawn checks. Uses chunk position in mob count map.
- **Multithreaded tracker** — Offloads entity tracking (which players see which entities) to background threads.
- **Prevent entities moving into weak loaded chunks** — Projectiles/entities that would trigger chunk loads are discarded instead. Fixes memory leaks from weak-loaded projectiles.

### Pufferfish-specific
- **Async mob spawning** — Offloads mob spawning computation to async thread.
- **Async entity tracker** (+ exclusive) — Completely offloads entity tracking to async thread.
- **Async pathfinding** (+ exclusive) — Offloads pathfinding to async threads.
- **DAB (Dynamic Activation of Brain)** — Throttles entity AI based on distance from players. Configurable start distance, tick frequency, activation mod.
- **Suffocation optimization** — Rate-limits suffocation checks.
- **Inactive goal selector throttle** — Prevents inactive entities from selecting new pathfinder goals.
- **Projectile chunk loading limits** — Max loads per tick and per projectile lifetime.
- **Entity timeouts** — Configurable timeouts for projectile entities.
- **SIMD map rendering** — 8x faster map rendering using vectorization.
- **30% faster hoppers** (from Airplane).
- **Fast raytracing** — Improves line-of-sight performance (villagers etc).

### Gale
- **Faster threading system** — Custom threading for terrain generation (2-3x faster on 1.19, not yet ported to 1.20+).
- **XorShift random** — Faster random number generation for non-worldgen uses (elytra, tree gen, lightning, lootables).
- **Reduced sensor work** (Petal) — Cache line-of-sight lookups, reduce sensor update frequency.
- **Micro-optimizations** from Lithium and Airplane (carefully reviewed).

### Lithium (CaffeineMC mod)
- **Faster hash palette** — Optimized chunk palette (HashMapPalette -> LithiumHashPalette). Replaces the default palette for bits 3-8 with a faster implementation. Ported into Leaf.
- Various other micro-optimizations for block state, collision, etc.

---

## Configuration Tuning (already available in Paper/Sugarcane)

These settings in `paper-global.yml` / `paper-world-defaults.yml` can be tuned for better chunk gen performance with pre-generated worlds:

```yaml
chunk-loading-basic:
  player-max-chunk-generate-rate: -1.0    # unlimited during pregen
  player-max-chunk-load-rate: -1.0       # unlimited during pregen
  player-max-chunk-send-rate: -1.0       # unlimited during pregen

chunk-loading-advanced:
  player-max-concurrent-chunk-generates: -1   # auto, or set high
  player-max-concurrent-chunk-loads: -1      # auto, or set high

chunk-system:
  gen-parallelism: default
  io-threads: -1          # auto, or set to 2-4 for NVMe
  worker-threads: -1      # auto, or set higher for pregen

chunks:
  delay-chunk-unloads-by: 10s
  max-auto-save-chunks-per-tick: 8
  prevent-moving-into-unloaded-chunks: true
```

For pre-generation, you can also pass `-DPaper.WorkerThreadCount=16` to boost parallelism.

---

## Potential Patches to Port (in priority order)

### 1. Entity / Projectile Chunk Loading Protection
**Source**: Leaf (`Prevent entities from moving into weak loaded chunks`) / Pufferfish (`max-loads-per-projectile`)
**Why**: Prevents projectiles from loading chunks and causing sync loads / memory leaks.
**Effort**: Medium. Requires adding config options and hooking into entity movement.

### 2. Async Chunk Sending
**Source**: Leaf (`async-chunk-sender`)
**Why**: Encoding and sending chunk data to players off the main thread. Big win for join/teleport lag.
**Effort**: Medium. Replaces the sync chunk packet encoding path.

### 3. Faster Hash Palette (Lithium)
**Source**: Leaf / Lithium (`LithiumHashPalette`)
**Why**: Faster chunk palette for bits 3-8. Micro-optimization but broadly applicable.
**Effort**: Low. Single class addition + palette factory changes.

### 4. Async Mob Spawning
**Source**: Pufferfish / Leaf
**Why**: Offloads mob spawn count calculations to async thread. Avoids sync chunk loads during spawn.
**Effort**: Medium. Needs careful state management.

### 5. Async Pathfinding
**Source**: Petal / Pufferfish+ / Leaf
**Why**: Pathfinding is the bulk of entity ticking. Offloading it frees main thread.
**Effort**: High. Complex threading, compatibility concerns.

### 6. Multithreaded Entity Tracker
**Source**: Leaf / Petal / Pufferfish+
**Why**: Calculating which players see which entities is expensive at high player counts.
**Effort**: High. Threading complexity, packet ordering guarantees.

### 7. DAB (Dynamic Activation of Brain)
**Source**: Pufferfish
**Why**: Throttles entity AI tick rate based on distance. Big win for servers with many entities.
**Effort**: Medium. Already partially covered by EAR 2.0 in Moonrise, but DAB is more sophisticated.

### 8. Fast Raytracing / Line of Sight Cache
**Source**: Gale / Petal / Pufferfish
**Why**: Villager line-of-sight checks are expensive. Caching + reducing update frequency helps.
**Effort**: Low. Targeted cache changes.

---

## Key People / Projects to Watch

- **Spottedleaf** — Author of Moonrise, Starlight, chunk system rewrite, Folia. Most of the heavy lifting.
- **PaperMC/Paper** — Upstream. Most optimizations land here eventually.
- **Winds-Studio/Leaf** — Aggressive performance fork. Best source for cherry-pickable patches since they maintain clean patch files and credit sources.
- **pufferfish-gg/Pufferfish** — DAB, async systems. Some features are Pufferfish+ (paid) only.
- **GaleMC/Gale** — Merged into Leaf. Good micro-optimizations.
- **CaffeineMC/lithium** — Original source for many micro-optimizations (LGPL).

---

## Note on UniverseSpigot

UniverseSpigot is a closed-source paid fork (~$60-70). Claims 40-60% performance improvement. Based on Pufferfish lineage. Sugarcane's goal is to provide a free alternative by cherry-picking the best open-source optimizations from Paper ecosystem forks.

Paper's Moonrise chunk system (already in Sugarcane) already solves the main chunk gen bottleneck. The remaining gains come from:
1. Async entity/pathfinding/tracker systems
2. Entity AI throttling (DAB)
3. Chunk loading protection
4. Async chunk sending
5. Micro-optimizations (palette, random, line-of-sight cache)
