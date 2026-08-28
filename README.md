# ViewTune
Per-player view **and** simulation distance for Paper 1.21+/26.x, plus a TPS-adaptive ceiling.

`view-distance=10` loads the same 441 chunks for everyone, including the players running their client
at 6. ViewTune gives each player what their client actually asked for, between your floor and your
ceiling — and when TPS drops it lowers that ceiling for everyone one chunk at a time, raising it back
once the server recovers.

- Per-player view distance from the client's own setting
- Per-player **simulation distance** (mobs, redstone, crops), kept at or below the view distance
- TPS regulator with a floor, hysteresis and one-chunk steps
- Per-rank caps (`viewtune.view.<n>`, `viewtune.sim.<n>`), per-world limits, player-count scaling
- Raising is instant, lowering is delayed — no chunk churn when a player drags the slider
- `/viewtune savings` reports the chunks you are not loading

Commands: `/viewtune [status|players|savings|reload]` (alias `/vt`) · Permission: `viewtune.admin`

## Tested on
Paper 1.21.11 and Paper 26.2 — runtime-tested, not just "it loads". The decision logic is asserted on
both live servers **against the shipped jar's own class**, loaded through the plugin's classloader so
the assertions cannot drift from a copy: clamping against ceiling, floor and Minecraft's hard minimum
of 2, unknown client settings, the simulation/view coupling, every branch of the TPS regulator
including its floor and ceiling, player-count thresholds and the savings arithmetic —
19 assertions, green on both versions.

## Niche
The established plugin for per-player view distance (~57k downloads) last shipped in October 2023;
the runner-up has under 2,000 downloads and has been silent almost as long. The old jar still loads on
current Paper, so this is not a rescue — it is a larger tool in a slot nobody has tended for nearly
three years, and simulation distance, the TPS regulator, per-rank caps, per-world limits and
player-count scaling are new.

## License
MIT — see [LICENSE](LICENSE).

## Development note
This project is **AI-assisted**: the code is written with Claude under the direction, testing and
release approval of the maintainer. Every release is run against a live Paper server before it ships.
