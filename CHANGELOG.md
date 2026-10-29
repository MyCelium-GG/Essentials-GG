# Changelog

All notable changes to Essentials GG are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Essentials GG is versioned semantically (`MAJOR.MINOR.PATCH`).
It runs on Minecraft 26.3, 26.2 and 26.1 (Fabric + Forge + NeoForge, Java 25) and requires
[Mycel](https://github.com/MyCelium-GG/MyCel-Lib) (`my.celium.org`).

## [1.0.0] - 2026-10-29

### Added

- First release: homes, warps, spawn, teleport requests, back, random
  teleport, kits, economy, moderation (tempban, mutes, warnings, freeze),
  chat format with hex colors and gradients, private messages, nicknames,
  AFK, utility commands, portable workstations, MOTD and rules, sidebar,
  tab list and Discord webhooks.
- Chest menus for homes, warps, kits and random teleport.
- Six languages (English, German, Spanish, French, Dutch, Brazilian
  Portuguese), chosen per player.
- LuckPerms support on all three loaders.
- `/ess migrate essentialsx` importer with a dry run.
- Kit editor menus (`/kitedit`): contents, cooldown presets, toggles,
  icon, rename, preview, delete.
- Random teleport menu showing each dimension's state and cooldown.
- `/perm`: built-in permissions for players and groups, with wildcards,
  prefixes and suffixes.
- `/ess help`; `/ess` only lists its own subcommands and commands whose
  name is taken.
