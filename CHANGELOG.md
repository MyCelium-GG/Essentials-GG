# Changelog

All notable changes to Essentials GG are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Essentials GG is versioned semantically (`MAJOR.MINOR.PATCH`).
It runs on Minecraft 26.3, 26.2 and 26.1 (Fabric + Forge + NeoForge, Java 25) and requires
[Mycel](https://github.com/MyCelium-GG/MyCel-Lib) (`my.celium.org`).

## [1.1.0] - 2026-10-30

### Added

- `/menu` (alias `/gui`): one menu leading to homes, warps, kits and random
  teleport, showing only what the player may use.
- Homes menu: a button to set a home where you stand, and right-click on a
  home to teleport, rename, move it, pick its icon or delete it.
- Distance to homes and warps in their tooltips; every menu has a back button
  where there is somewhere to go back to.

### Changed

- All menus share one look: a framed, centered list with a bottom row for
  back, page arrows and page number. Lists are sorted by name.
- Paging and toggles update the menu in place, so the cursor stays put; clicks
  make a sound, and something unavailable makes a refusal sound.
- Icons no longer show item stats, and panes no longer show an empty tooltip.
- The kit editor returns to the kit after closing its contents chest, and
  marks the current cooldown with a shimmer.
- Empty lists open with a note instead of a chat message; dimension names are
  translated.

### Fixed

- A shift-click in the homes menu no longer deletes a home without asking:
  deleting now goes through a confirmation.
- A kit preview shows all 54 stacks, not just the first 45.
- A double-click can no longer land on the next menu's button.

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
