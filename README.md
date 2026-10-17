# 🌿 Sugarcane

**A vanilla-faithful technical fork of Paper**

Sugarcane is a custom Minecraft server fork designed to restore vanilla technical mechanics while retaining the stability and plugin ecosystem of Paper.

*Paper optimizes gameplay. Sugarcane restores it.*

---

## 🎯 Purpose

Sugarcane exists for servers that rely on technical Minecraft behavior, including:

- TNT cannons and dupers
- Large-scale redstone machines
- Mob switches and advanced farms
- Cross-chunk and long-running contraptions

Many of these mechanics are altered or limited in Paper for performance reasons. Sugarcane removes those limitations and brings behavior closer to true vanilla parity.

> [!WARNING]
> **Important: World Pregeneration Recommended**
> 
> Sugarcane prioritizes correctness over performance safeguards. Because of this:
> - Chunk loading is less restricted
> - Machines can load chunks more aggressively
> - High-throughput contraptions (TNT, hoppers, etc.) are no longer throttled
> 
> 👉 **You should use Sugarcane on pregenerated worlds.**
> 
> **Why?** Vanilla-style mechanics can generate chunks rapidly under load, cause lag spikes during exploration, and increase disk and CPU usage if terrain is generated on-the-fly.
> 
> **Recommended setup:** Pre-generate your world using tools like Chunky or WorldBorder fill, then run Sugarcane for gameplay.
> 
> Exploration on ungenerated terrain is noticeably cheaper as of build 7 — see `delay-view-distance-unloads-by`
> below — but generating terrain on demand is still the most expensive thing a server does, so pregeneration
> remains the recommendation.

---

## 🔧 Key Changes

Sugarcane restores or improves the following behaviors:

### 💣 Technical Mechanics
- **Removes TNT per-tick limits** → high-rate cannons work flawlessly
- **Enables block exploits** → restores piston duplication, headless pistons, and bedrock breaking
- **Standardizes armor stands** → restores vanilla armor stand behavior (no unintended breaking via sweeps/cramming)

### 🌍 Chunk & World Behavior
- **Removes chunk load rate limits** → ensures fast chunk processing
- **Restores cross-chunk interactions** → hoppers, fluids, and double containers work across chunk boundaries
- **Prevents premature chunk unloads** → tick tickets properly process without desynchronization in massive machines
- **Separates ticking and view-distance unload delays** → exploration no longer thrashes chunks off and back onto disk, without extending how long machines tick

### 🚃 Entity & Redstone Reliability
- **Increased activation range** → minecarts and misc entities now activate properly over longer distances
- **Fixes piston bolts** → protects long-distance transport systems from skipping ticks

### 👥 Mob Spawning
- **Improves per-player mob spawning** → provides granular control over mobcap influence radius
- **Restores global mob cap behavior** → global mob switches work again

### 🧑‍🌾 Villagers
- **Fixes villager POI searches** → restores vanilla's 48 block search range, and makes the searches cheaper
- **Purpur's villager options** → lobotomize stuck villagers, configurable search radius, iron golem limit

### 🛠️ Automation & Crafting
- **Raises recipe spam limits**
- **Relaxes packet limits** → crafting automation systems no longer cause kicks or stalls

### 🔌 Plugin Compatibility
- **Classic version string** → `Bukkit.getBukkitVersion()` reports `26.3-R0.1-SNAPSHOT` rather than Paper
  26.x's `26.3.build.9-alpha`, so plugins that read it the way they always have find a Minecraft version
  instead of a build string. The build number and channel are unchanged in `/version`, `ServerBuildInfo`
  and the jar manifest.

---

## 🧵 Forced Folia Mode

Sugarcane can pretend to be a Folia build. This exists so plugin authors can develop against the Folia
scheduler API on top of Sugarcane's vanilla patches, without running Folia itself and without Paper's
main-thread scheduler quietly propping up code that would break on a real regionised server.

### ⚙️ Configuration

Located in `paper-global.yml`:

```yaml
sugarcane:
  folia:
    force-folia: false
    strict-scheduler: true
```

### 🔍 Behavior

With `force-folia: true`:

- Plugins must declare `folia-supported: true` in `plugin.yml` or `paper-plugin.yml`. Anything that does
  not is refused with the same message a real Folia server gives, before any of its code runs — including
  its bootstrapper.
- With `strict-scheduler: true` (the default), the legacy `BukkitScheduler` throws
  `UnsupportedOperationException`, exactly as it does on Folia. Plugins must use
  `Bukkit.getGlobalRegionScheduler()`, `Bukkit.getRegionScheduler()`, `Bukkit.getAsyncScheduler()`, or
  `Entity#getScheduler()`.

The setting is read before plugins load, so it needs a restart and is not affected by `/paper reload`.
Both options default to off, so nothing changes unless you opt in.

### ⚖️ What this is not

Sugarcane is **not** regionised — it is still a single-threaded server. Forced Folia mode reproduces
Folia's *plugin contract*, not its threading model. Code that passes here will not have been exercised
against real concurrent region ticking, so it is a development aid, not a substitute for testing on Folia.
Folia limitations that exist purely because of region threading (scoreboards, synchronous teleports) are
deliberately left working, since Sugarcane can support them correctly.

---

## 🗺️ Exploration & Chunk Unloading

Paper ships a single `delay-chunk-unloads-by` setting that does two unrelated jobs: it keeps a chunk
**ticking** after it leaves simulation distance, and it keeps a chunk **loaded** after it leaves view
distance. Sugarcane wants opposite things from those two, so it splits them.

Holding a chunk at its ticking level is what makes machines run longer than vanilla, so Sugarcane keeps
that at `0s`. But dropping a chunk the instant it leaves view distance is what makes exploration expensive:
walk forward and the chunks behind you are written out and unloaded, turn around and they are read back off
disk. A loaded-but-not-ticking chunk costs memory and nothing else, so holding those is free correctness-wise.

### ⚙️ Configuration

Located in `paper-world-defaults.yml` (or per-world config):

```yaml
chunks:
  # Keeps chunks TICKING after leaving simulation distance.
  # 0s = vanilla parity; machines stop exactly when vanilla stops them.
  delay-chunk-unloads-by: 0s
  # Keeps chunks LOADED (not ticking) after leaving view distance.
  # Absorbs the unload/reload churn of exploring fresh terrain.
  delay-view-distance-unloads-by: 10s
```

### ⚖️ Tradeoffs

- ✅ Exploration stops re-reading chunks you just walked away from
- ✅ Machine ticking behaviour is unchanged — the ticking delay is still `0s`
- ⚠️ Costs memory proportional to how fast players move through new terrain
- Set `delay-view-distance-unloads-by: 0s` to get the previous build's behaviour back

Build 7 also ports several allocation and serialization optimizations from
[Leaf](https://github.com/Winds-Studio/Leaf) that sit directly on the exploration path: bulk long-array
writes when serializing chunk sections and light data, iterator-free surface rule evaluation, reuse of the
ore-placement bit sets, and cheaper structure terrain-adaptation math (Paper now ships that last one itself).

---

## 🌱 Localized Mobcaps

Sugarcane improves upon Paper's per-player mob spawns by providing granular control over mobcap influence radius while preserving vanilla-like local behavior.

### 🧠 What it does

Localized Mobcaps enhances Paper's per-player spawning system by allowing you to configure exactly how far each player's mobcap influence extends. This prevents distant players from interfering with each other's farms while maintaining natural local spawning behavior.

When enabled:

Mobcaps are evaluated locally around each player with a configurable radius
Only mobs within the configured radius contribute to spawn limits
Distant players no longer interfere with each other's farms
Nearby players still share local spawning areas (more natural than strict private pools)

### ⚙️ Configuration

Located in paper-world-defaults.yml (or per-world config):

```
sugarcane:
  spawning:
    local-mobcap:
      enabled: true
      # Radius in chunks for local mobcap calculation.
      # Vanilla uses ~8 chunks (128 blocks).
      # Lower = more isolated players, stronger mob switches
      # Higher = more shared spawning, less isolation
      count-radius-chunks: 8
      ignore-spectators: true
      use-full-vertical-range: true
```

### Defaults:

Enabled by default to provide a better-than-Paper experience with configurable radius
Recommended radius: 8 chunks (128 blocks) - matches vanilla spawn distance

### 🔍 Behavior

Mobcaps are scaled based on nearby chunks (vanilla-style scaling)
Only mobs within the configured radius count toward a player's spawning limits
Nearby players still influence each other (more natural than strict per-player pools)
Distant players are effectively isolated, preventing cross-region interference

### ⚖️ Tradeoffs

This feature improves upon Paper's per-player mob spawns:

✅ Configurable influence radius (not fixed at spawn distance)
✅ More natural local behavior (nearby players share areas)
✅ Prevents cross-player mobcap interference at distance
✅ Farms work independently at configurable distances
⚠️ Behavior differs from vanilla global caps
⚠️ Global mob switches no longer affect the entire server

### 🎯 When to use it

Enable this if:

Your server has multiple active players in different areas
Players are unintentionally breaking each other’s farms
You want a better multiplayer experience without full per-player mobcaps

Leave it disabled if:

You want strict vanilla behavior
Your server relies heavily on global mob switches

This keeps it:

clearly optional
framed as a Sugarcane-specific feature
aligned with your “vanilla-first” philosophy without contradicting it

---

## 🧑‍🌾 Villager Performance

On servers with big trading halls, breeders and iron golem farms, most of the villager brain's time goes on
villagers searching for a bed, job site or bell they cannot claim. Sugarcane fixes the two upstream problems that
made those searches expensive, and ports Purpur's villager options for servers that want to go further.

### 🔧 Always on

- **Vanilla POI search range** → since 26.1, Paper's villager POI search ignored vanilla's 48 block distance limit.
  Villagers could pick beds and workstations far above or below them, and every search swept the whole world
  height. Sugarcane restores the 48 block sphere.
- **Faster POI searches** → POI searches track the chunk sections they have visited in a bitset instead of a hash
  set. They visit sections in the same order, so results are identical.
- **Behavior tick rates work again** → `tick-rates.behavior.villager.acquirepoi` and `validatenearbypoi` had no
  effect, because those behaviors are no longer separate classes. They now throttle the behaviors as Paper
  documents.

### ⚙️ Configuration

Located in paper-world-defaults.yml (or per-world config):

```yaml
sugarcane:
  villagers:
    lobotomize:
      enabled: false
      check-interval: 100
      wait-until-trade-locked: true
    search-radius:
      acquire-poi: 48
      nearest-bed-sensor: 48
    spawn-iron-golem:
      radius: 0
      limit: 0
```

- **`lobotomize`** (from Purpur) → villagers that cannot walk to any neighbouring block stop running their AI.
  They still trade, level up and restock. With `wait-until-trade-locked`, only villagers that have been traded with
  are affected, so villagers you are still re-rolling and iron golem farm villagers keep working.
- **`search-radius`** (from Purpur) → how far villagers look for beds, job sites and bells (`acquire-poi`), and how
  far baby villagers look for beds (`nearest-bed-sensor`).
- **`spawn-iron-golem`** (from Purpur) → when `radius` is above 0, villagers stop summoning iron golems while more
  than `limit` golems are within `radius` blocks.

Paper's own throttle now works for these behaviors too:

```yaml
tick-rates:
  behavior:
    villager:
      acquirepoi: 120 # look for an unclaimed bed, job site or bell at most every 6 seconds
```

### ⚖️ Tradeoffs

- ✅ The always-on changes match vanilla behavior
- ⚠️ Lobotomized villagers do not sleep, panic, gossip or breed, so keep `wait-until-trade-locked` on if any iron
  golem farm has villagers that cannot move
- ⚠️ A slower `acquirepoi` tick rate means villagers take longer to claim new beds and workstations, and a
  smaller `acquire-poi` radius means they ignore ones further away

## 🚀 When to Use Sugarcane

**Use Sugarcane if your server focuses on:**
- Technical Minecraft
- Large-scale automation
- Redstone engineering
- Vanilla-accurate mechanics

**Do not use Sugarcane if your goal is:**
- Maximum player count
- Heavy survival SMP with exploration-based generation
- Strict performance optimization

---

## 🧪 Status

This project is actively tested against:
- TNT cannons
- Portal-based machines
- Mob switches
- Piston bolts
- Cross-chunk automation

*Feedback and edge cases are welcome.*

---

## 📦 Building

To fetch the latest Vanilla mappings, apply the Sugarcane patches, and compile the executable paperclip jar:

```bash
./gradlew applyPatches
./gradlew createMojmapPaperclipJar
```

**Output jar:**
`build/libs/paper-paperclip-<version>-mojmap.jar`

---

### 💬 Final Note

Sugarcane isn’t trying to be faster than Paper.  
**It’s trying to be truer than Paper.**