# ViewTune

**Give every player the view distance they actually asked for — and take it back automatically when the server gets busy.**

---

## The problem

`view-distance=10` in `server.properties` loads the same 441 chunks for everybody. But a large part of
your players run their render distance at 6 or 8 — on a laptop, on a handheld, or because they simply
prefer the frame rate. The server loads, keeps, and sends chunks those players will never see.

## What ViewTune does

It reads what each client asked for and gives that player exactly that much — never more than your
ceiling, never less than your floor. A player rendering at 6 chunks stops costing you a 10-chunk radius.

Then it does the part nobody else does: **when the server starts struggling, it lowers the ceiling for
everyone, one chunk at a time, and raises it back once TPS recovers.** No admin awake at 3 a.m., no
restart, no flat "everybody gets 6 forever" compromise.

## Why this plugin exists

The plugin that popularised per-player view distance has around 57,000 downloads and its last release
was in **October 2023**. Nothing else in this niche is maintained either — the runner-up has under
2,000 downloads and has been silent almost as long.

**An honest note:** the old plugin still loads on current Paper. This is not a rescue mission; it is a
bigger tool in a slot that has been unattended for nearly three years. Simulation distance, the TPS
regulator, per-rank caps, per-world limits and player-count scaling are all new here.

## Features

- **Per-player view distance** from the client's own render-distance setting
- **Per-player simulation distance** — the expensive one: mobs, redstone, crops, hoppers.
  Kept at or below what the player can actually see.
- **TPS-adaptive ceiling** — drops one chunk per interval below your low-TPS mark, climbs back above
  your recovery mark, never below your floor. Gradual on purpose: a jump from 16 to 4 would itself be
  a lag spike, because every chunk gets re-sent.
- **Per-rank caps** via `viewtune.view.<n>` and `viewtune.sim.<n>` — the obvious donor perk, and
  it works with any permissions plugin
- **Per-world limits** — a lobby does not need what a survival world needs
- **Player-count scaling** — tighten the ceiling as the server fills up
- **Anti-thrash** — raising is instant, lowering waits, so a player dragging the slider does not
  churn the chunk system. Joins and world changes apply at once, where there is nothing to thrash.
- **`/viewtune savings`** tells you how many chunks are not being loaded right now
- Folia-supported, no dependencies, one small jar

## Commands

| Command | What it does |
|---|---|
| `/viewtune` | TPS, current adaptive ceiling, server defaults, player count |
| `/viewtune players` | Per player: what the client asked for, what it got, which world |
| `/viewtune savings` | Chunks not loaded right now, versus a flat view distance |
| `/viewtune reload` | Reload the config |

Alias: `/vt`. Permission: `viewtune.admin` (op by default).

## Configuration

```yaml
interval-seconds: 5

view:
  min: 2
  max: -1                   # -1 = use server.properties
  lower-delay-seconds: 10

simulation:
  enabled: true
  min: 2
  max: -1
  never-above-view: true

adaptive:
  enabled: true
  low-tps: 18.0
  recover-tps: 19.5
  floor: 4

player-scaling:
  enabled: false
  thresholds:
    - players: 40
      max-view: 8
    - players: 80
      max-view: 6

permission-caps:
  enabled: true
  scan-up-to: 32

worlds: {}
```

## Compatibility

Built against the Paper API 1.21 and up. Every release is started on a **live Paper 1.21.11 server and
a live Paper 26.2 server**, and the decision logic is asserted there against the shipped jar — not a
copy of it: client-below-ceiling, ceiling-below-client, floor and hard-minimum clamping, unknown client
settings, the simulation/view coupling, every branch of the TPS regulator including its floor and
ceiling, the player-count thresholds, and the savings arithmetic. **19 assertions, green on both
versions.**

## Updates

Fast updates on new Minecraft versions are the reason this plugin exists rather than a fourth
abandoned one.

## Source & licence

MIT licensed, source on [GitHub](https://github.com/arvidhqx-commits/viewtune).

## Development note

This project is **AI-assisted**: the code is written with Claude under the direction, testing and
release approval of the maintainer. Every release is run against a live Paper server before it ships.
