# Vill Overlay

A Hypixel Bed Wars stats overlay for Minecraft 1.8.9 (Forge). It reads the
players in your current Bed Wars lobby, looks up each one's stats, and draws a
compact HUD that ranks them and flags the dangerous ones. It also shows
generator timers, raises threat alerts, checks players against a shared
cheater/sniper blacklist, keeps your own local tags with notes, and can write
short commentary on the lobby.

It only does anything while you are in a Bed Wars game (Hypixel, with a
`BED WARS` sidebar). It reads the lobby passively from the tab list and never
auto-types commands like `/who`. Lookups are throttled to at most once every 30
seconds per player, so you stay well under any API limit.

## Features

- **Stats HUD**: star, FKDR, WLR and winstreak per player, sorted by a threat
  index and colour-coded so the sweats are easy to spot.
- **Generator timers**: approximate diamond and emerald spawn countdowns.
- **Commentary**: an instant offline heuristic line (who is the threat, who is
  free, how many sweats and nicks), plus optional AI commentary on a keybind.
- **Threat alerts**: a HUD banner and a one-shot sound the first time a scary
  player turns up (high star, high FKDR, known winstreak, tagged, or nicked).
- **Player tags and notes**: a local blacklist. Tag people as `sniper`,
  `cheater`, `friend` and so on, and attach notes. Danger tags raise an alert
  the next time you see that player.
- **Shared cheater/sniper blacklist**: optionally check every player against a
  community blacklist (Urchin by default) and flag known cheaters and snipers
  with a `☠`, sort them to the top, and raise an alert. Off by default; bring
  your own service and key.
- **Session history**: a local "seen this player xN before" counter.
- **Optional denicking**: resolve nicked players to a real account through a
  configurable third-party endpoint. Off by default; bring your own service.

Everything except the stats lookups stays local. Tags, notes and history are
plain JSON next to the config. There is no telemetry, and nothing leaves your
machine except the lookups you send to whichever provider you configure.

## Stats providers

The lookup source is pluggable:

- **`hypixel`** (default, recommended): the official Hypixel API with your own
  key. Get a free key at <https://developer.hypixel.net>, then set it in-game.
- **`proxy`**: any third-party service that returns Hypixel player-format JSON
  (`{success, player}`). You set the URL template in the config (`{key}` and
  `{uuid}` are substituted), so no endpoint is forced. There is an unverified
  Antisniper default you are meant to replace with whatever service you use.

No API keys ship with this mod. You supply your own, and they live in your local
config file in plain text, so keep that file private.

## Shared blacklist

On top of your own tags, the overlay can check each player's UUID against a
shared community cheater/sniper blacklist. It is off by default. Turn it on with
`/vill blacklist on` and set a key with `/vill blacklist key <key>`. Flagged
players get a `☠`, sort to the top, and trip a threat alert.

The lookup URL is a template, same as the stats `proxy`: `{key}` and `{uuid}`
are substituted, so it is not tied to one service. The default points at
[Urchin](https://urchin.ws) (response `{uuid, tags: [...]}`, flagged when `tags`
is non-empty); parsing also accepts a top-level `type` string or a true
`flagged`/`cheater`/`sniper` boolean. Point it at whatever blacklist you use.

## Build and install

You need a JDK to run Gradle (Java 17 or newer works; the Java 8 toolchain that
compiles the mod is fetched automatically). Then:

```
./gradlew build
```

Drop `build/libs/villoverlay-1.0.0.jar` into your 1.8.9 Forge `mods` folder.

## First-time setup

1. Get a free Hypixel API key at <https://developer.hypixel.net>.
2. In game, run `/vill key <your-hypixel-api-key>`.
3. Join a Bed Wars game. The overlay shows up on its own.

## Commands

All under `/vill` (aliases `/bw` and `/villoverlay`):

| command | what it does |
|---|---|
| `/vill key <key>` | set your Hypixel API key |
| `/vill provider <hypixel\|proxy>` | choose the stats source |
| `/vill proxykey <key>` | key for the proxy provider |
| `/vill refresh` | force a stats refresh now |
| `/vill toggle` | turn the overlay on or off |
| `/vill hud <x> <y>` | move the HUD |
| `/vill ai` | run AI commentary on the current lobby |
| `/vill status` | show provider, key and roster state |
| `/vill tag <player> <label>` | tag a player (`sniper`, `cheater`, `friend`, ...) |
| `/vill untag <player>` | remove a tag |
| `/vill note <player> <text>` | attach a note |
| `/vill tags` | list everyone you have tagged |
| `/vill alert ...` | tune the threat-alert thresholds |
| `/vill denick <on\|off\|url ...\|key ...>` | configure optional denicking |
| `/vill blacklist <on\|off\|url ...\|key ...>` | configure the shared cheater/sniper blacklist |
| `/vill seen <player>` | how often you have seen them before |

## Keybinds

Under Controls, group "Vill Overlay" (all unbound by default): toggle the
overlay, force a refresh, and run AI commentary.

## AI commentary

The `/vill ai` keybind shells out to your local `claude` CLI in print mode
(`claude -p`). That runs on your Claude Code subscription login, not the metered
Anthropic API, so it costs no usage credits. If `claude` is not on the PATH that
Minecraft was launched with, set the full path with the `claudePath` config
option. AI commentary is optional; the offline heuristic line needs no setup.

## Configuration

Settings live in `config/villoverlay.cfg` (Forge config format) and can all be
changed in-game with `/vill`. Tags, notes and session history sit next to it as
plain JSON (`villoverlay-tags.json`, `villoverlay-history.json`).

## Credits

Built on [nea89o's Forge 1.8.9 template](https://github.com/nea89o/Forge1.8.9Template)
(Unlicense/CC0). The Bed Wars star formula and stat field paths were verified
against [Amund211/prism](https://github.com/Amund211/prism).

## License

Public domain, under the [Unlicense](LICENSE).
