# GrowFarm

GrowFarm is a Paper plugin that automatically grows crops, nether crops, and
trees for players as they walk or sneak around — no more punching wheat by
hand. It also supports auto-planting seeds on empty farmland/soul sand, and
integrates cleanly with WorldGuard and LuckPerms when they're installed.

Every toggle is per-player: nothing changes for players who don't opt in with
`/grow` or `/plant`.

## Features

- **AutoGrow - Move Grow**: walk over farmland/soul-sand crops and they grow
  as you pass. Never touches saplings.
- **AutoGrow - Sneak Grow**: sneak near crops **and** saplings to grow them
  (triggers instantly on starting to sneak, then keeps triggering as you move
  while sneaking).
- **AutoPlant**: automatically plants seeds on empty farmland and nether wart
  on soul sand as you walk, respecting a configurable seed priority order.
- **Saplings**: supports every vanilla 1x1 sapling and 2x2 giant tree (dark
  oak, jungle, etc.) automatically via vanilla's own bone-meal growth logic —
  no hardcoded tree list to maintain. Individual sapling types can be
  disabled, and growth can be instant or chance-based.
- **Resource requirements (optional, off by default except where noted)**:
  require bone meal, require a hoe (which takes durability damage and can
  break, respecting Unbreakable/Unbreaking), or require a custom list of
  items (all consumed simultaneously, e.g. `BONE_MEAL: 32` + `GOLD_ORE: 1`).
  Missing requirements show an on-screen warning (title for bone meal/hoe,
  chat message listing exact items needed for custom items), rate-limited to
  once every 5 seconds per player.
- **Independent settings per section**: farmland, netherland (soul sand +
  nether wart), and saplings each have their own area, growth chance, stage
  vs. instant growth, and resource requirements.
- **Exclusive mode**: optionally prevent AutoGrow and AutoPlant from being
  active at the same time per player.
- **WorldGuard integration** *(optional)*: two custom region flags,
  `autogrow` and `autoplant`, so region owners can deny either feature in
  specific regions. Servers without WorldGuard installed are completely
  unaffected — GrowFarm never touches WorldGuard's classes unless it detects
  WorldGuard is actually present.
- **LuckPerms integration** *(optional)*: all permission checks use the
  standard Bukkit permissions API, which LuckPerms hooks into automatically —
  no extra setup needed. The plugin also exposes a small LuckPerms hook for
  future features (e.g. reading a player's primary group).
- **FastAsyncWorldEdit** is a declared soft-dependency for future bulk-edit
  features; GrowFarm's own block edits are already lightweight and don't
  require it.
- Fully configurable colored messages (standard `&`-color-code format).

## Requirements

| Component   | Version                          |
|-------------|-----------------------------------|
| Server      | Paper 1.21.x (or a Paper fork)    |
| Java        | 17+                                |
| WorldGuard  | Optional - region flags            |
| LuckPerms   | Optional - permission management   |
| FastAsyncWorldEdit | Optional - reserved for future bulk-edit features |

GrowFarm works perfectly fine with none of the optional dependencies
installed - those integrations only activate if/when the corresponding
plugin is detected on the server.

## Installation

1. Download `GrowFarm-<version>.jar` (or build it yourself, see below).
2. Drop it into your server's `plugins/` folder.
3. Start/restart the server. `plugins/GrowFarm/config.yml` is generated
   automatically on first run.
4. Edit `config.yml` to taste and run `/grow reload` (no restart required).

## Commands

| Command | Aliases | Description |
|---|---|---|
| `/grow` | `/growfarm` | Toggles **both** Sneak Grow and Move Grow together. See toggle rules below. |
| `/grow sneak` | | Toggles Sneak Grow only. |
| `/grow move` | | Toggles Move Grow only. |
| `/grow reload` | | Reloads `config.yml` without restarting the server. Player toggles (Sneak/Move/AutoPlant) are preserved. |
| `/plant` | | Toggles AutoPlant. |

**Bare `/grow` toggle rule:**
- Sneak **and** Move both on → both turn **off**.
- Sneak **and** Move both off → both turn **on**.
- Mixed (one on, one off) → both turn **off**.

## Permissions

| Permission | Default | Description |
|---|---|---|
| `growfarm.*` | `false` | Grants every GrowFarm permission. |
| `growfarm.command.grow` | `true` | Use `/grow` to toggle Sneak + Move Grow together. |
| `growfarm.command.grow.sneak` | `true` | Use `/grow sneak`. |
| `growfarm.command.grow.move` | `true` | Use `/grow move`. |
| `growfarm.command.plant` | `true` | Use `/plant`. |
| `growfarm.command.reload` | `op` | Use `/grow reload`. |
| `growfarm.feature.autogrow` | `true` | Use AutoGrow at all (required alongside the sneak/move nodes below). |
| `growfarm.feature.autogrow.move` | `true` | AutoGrow triggers from movement (crops only, never saplings). |
| `growfarm.feature.autogrow.sneak` | `true` | AutoGrow triggers while sneaking (crops **and** saplings). |
| `growfarm.feature.autoplant` | `true` | Use AutoPlant. |

Each subcommand is gated by its own permission independently — a player
missing `growfarm.command.grow` can still use `/grow sneak` if they have
`growfarm.command.grow.sneak`, for example.

## Configuration

`config.yml` is organized into clearly separated sections:

- **`worldguard` / `luckperms`** — enable/disable each integration even if
  the corresponding plugin is installed.
- **`general.exclusive-mode`** — AutoGrow vs. AutoPlant exclusivity.
- **`farmlands`** — AutoGrow and AutoPlant settings for farmland crops
  (wheat, carrots, potatoes, beetroots, sugar cane, cocoa, melon/pumpkin
  stems, pitcher/torchflower crops, bamboo).
- **`netherland`** — AutoGrow and AutoPlant settings for soul sand + nether
  wart, configured independently from farmland (separate area, chance,
  requirements, etc).
- **`saplings`** — instant vs. chance-based growth, per-type enable/disable,
  and its own resource requirements.
- **`messages`** — every player-facing message, using standard `&`-color
  codes. `no-custom-items` supports a `%items%` placeholder that gets
  replaced with the exact items/amounts required for whatever section
  triggered it.
- **`debug`** — verbose console logging of every grow/plant/permission-deny
  decision, prefixed `[GrowFarm Debug]`. Leave `false` in production.

Each `autogrow`/`autoplant` block supports, independently per section:

```yaml
area: 4                # 1 = just the player's block, N = radius (N - 1) sphere
growth-chance: 50       # 0-100, percent chance per eligible block
stage-growing: true     # true = advance one stage, false = jump to max stage
bone-meal-use: false    # consume 1 bone meal per successful growth
hoe-use: false          # require + damage a hoe per successful growth
custom-item-use: false  # require a custom, simultaneously-consumed item set
custom-item-list:
  BONE_MEAL: 32
  GOLD_ORE: 1
```

See the shipped `config.yml` for the complete, commented reference with every
option and its default value.

### WorldGuard flags

When WorldGuard is installed and `worldguard.enabled: true`, GrowFarm
registers two custom region flags:

- `autogrow` — set to `deny` in a region to block AutoGrow there.
- `autoplant` — set to `deny` in a region to block AutoPlant there.

```
/rg flag <region> autogrow deny
/rg flag <region> autoplant deny
```

Both default to `allow`, so behavior outside any flagged region — or on
servers without WorldGuard at all — is completely unaffected.

## Building from source

```bash
git clone <this-repo-url>
cd GrowFarm
mvn clean package
```

The built jar is written to `target/GrowFarm-<version>.jar`.

## Support / Issues

Found a bug or have a feature request? Open an issue on this repository with:
- What you did (exact command/action).
- What you expected to happen.
- What actually happened (include console errors/logs if relevant).
- Your server version, GrowFarm version, and which optional plugins
  (WorldGuard/LuckPerms/FastAsyncWorldEdit) are installed.