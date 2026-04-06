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

### 🚃 Entity & Redstone Reliability
- **Increased activation range** → minecarts and misc entities now activate properly over longer distances
- **Fixes piston bolts** → protects long-distance transport systems from skipping ticks

### 👥 Mob Spawning
- **Disables per-player mob spawning** → enforces global caps
- **Restores global mob cap behavior** → global mob switches work again

### 🛠️ Automation & Crafting
- **Raises recipe spam limits**
- **Relaxes packet limits** → crafting automation systems no longer cause kicks or stalls

---

## 🧠 Design Philosophy

Sugarcane follows a simple principle:

> **If it works in vanilla, it should work here.**

- No arbitrary limits.
- No hidden behavior changes.
- No “optimized away” mechanics.

### ⚖️ Tradeoffs

Sugarcane intentionally removes several performance safeguards. This means higher CPU usage under heavy load, more aggressive chunk loading, and potential lag if used on non-pregenerated worlds.

**This is expected and intentional.**

---

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