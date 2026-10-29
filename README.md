# Essentials GG

[![License](https://img.shields.io/github/license/MyCelium-GG/MyCel-Lib)](LICENSE)

The server essentials, without the bloat. Homes, warps, spawn, TPA, back,
random teleport, kits, an economy, nicknames, chat with hex colors and
gradients, moderation, AFK, a sidebar and tab list, Discord webhooks and an
EssentialsX importer, around 90 commands in all. Every module can be
switched off, every message is editable, each player picks their
language. Server side: vanilla clients join as usual.

Built on [Mycel](https://github.com/MyCelium-GG/MyCel-Lib) (`my.celium.org`).

| | |
|---|---|
| Mod id | `essentialsgg` |
| Minecraft | **26.3**, **26.2**, **26.1** (26.1-26.1.2) · Java **25** |
| Loaders | Fabric, Forge, NeoForge |
| Side | Server (works in singleplayer and with vanilla clients) |
| Permissions | LuckPerms and any mod using fabric-permissions-api or the Forge/NeoForge PermissionAPI; op levels otherwise |

## What it does differently

- **Vanilla commands stay vanilla.** Essentials GG never takes a name
  someone else registered (`/msg`, `/ban`, `/kick`, `/tp` stay as they are).
  A command whose name is taken moves under `/ess <command>`, so nothing is
  lost; `/ess help` lists the important ones.
- **Off means gone.** A module that is off registers no commands and does
  no work. Single commands can be turned off too.
- **One place for data.** Two JSON files per world
  (`<world>/data/essentialsgg/players.json` and `server.json`), written in
  the background, not one file per player.
- **Text that looks good.** Every message, nickname, chat line, the MOTD,
  sidebar and tab list take legacy codes (`&c`, `&#ff8800`) and MiniMessage
  tags (`<#ff8800>`, `<gradient:red:gold>`, `<rainbow>`, `<b>`,
  `<hover:show_text:'...'>`, `<click:run_command:'/spawn'>`).
- **Menus that vanilla clients understand.** Homes, warps, kits and RTP
  open chest menus, kits are edited in menus (`/kitedit`), and `/craft`,
  `/anvil` and friends open the real screens.
- **Permissions without a permissions mod.** `/perm` gives players and
  groups Essentials GG permissions, prefixes and suffixes. LuckPerms works
  too.
- **Your language.** English, German, Spanish, French, Dutch and Brazilian
  Portuguese ship in the jar. Players follow their client's language or
  pick one with `/language`. Drop more files into
  `config/essentialsgg/lang/`.
- **Coming from EssentialsX?** `/ess migrate essentialsx` imports
  users, homes, nicknames, money, mutes, warps and the spawn, with a dry
  run first.

## Modules and commands

| Module | Commands |
|---|---|
| teleport | `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny`, `/tpacancel`, `/tptoggle`, `/back`, `/top`, `/tphere`, `/tpall` |
| homes | `/home [name]`, `/sethome [name]`, `/delhome <name>`, `/homes [player]` (menu) |
| warps | `/warp [name]` (no name: menu), `/setwarp <name> [restricted]`, `/delwarp <name>` |
| spawn | `/spawn [player]`, `/setspawn [firstjoin]` |
| rtp | `/rtp` (menu: each dimension with its lock and cooldown state), `/rtp <overworld\|nether\|end>` |
| kits | `/kit [name] [player]` (no name: menu, right-click previews), `/createkit <name> [cooldown\|once]`, `/delkit <name>`; `/kitedit` (menu editor, below) |
| economy | `/balance [player]`, `/pay <player> <amount>`, `/baltop [page]`, `/eco give\|take\|set\|reset <player> [amount]` |
| moderation | `/tempban <player> <time> [reason]`, `/unban`, `/mute <player> [reason]`, `/tempmute <player> <time> [reason]`, `/unmute`, `/warn <player> <reason>`, `/warnings [player]`, `/clearwarnings`, `/freeze <player>` |
| chat | `/broadcast <message>`, `/clearchat`; chat format, colors by permission, `@mentions` with a ping, slow mode |
| messaging | `/m`, `/pm`, `/dm <player> <message>` (`/msg` stays vanilla), `/reply`, `/ignore [player]`, `/msgtoggle`, `/socialspy` |
| nicknames | `/nick <nick\|off>`, `/nick player <player> <nick>`, `/realname <nick>` |
| afk | `/afk`; automatic AFK and optional kick |
| utility | `/fly`, `/god`, `/heal`, `/feed`, `/gmc`, `/gms`, `/gma`, `/gmsp`, `/ext`, `/ping`, `/playtime`, `/getpos`, `/speed`, `/hat`, `/more`, `/repair [all]`, `/invsee`, `/enderchest`, `/near`, `/seen`, `/whois`, `/suicide`, `/day`, `/night`, `/sun`, `/storm` |
| workbenches | `/craft`, `/anvil`, `/grindstone`, `/stonecutter`, `/smithing`, `/loom`, `/cartography`, `/trash` |
| info | `/motd`, `/rules`; MOTD on join, welcome line for new players |
| sidebar | `/sidebar` toggles it per player (module off by default) |
| tablist | header, footer, nicknames, prefixes and `[AFK]` in the tab list |
| discord | webhook for joins, leaves, chat, deaths and punishments (off by default) |
| permissions | `/perm user <player> [add\|remove\|check] <node>`, `/perm user <player> group add\|remove <group>`, `/perm group <group> create\|delete\|info\|add\|remove\|prefix\|suffix\|weight`, `/perm groups`, `/perm nodes [filter]` |
| core | `/ess [help\|version\|reload\|modules\|migrate essentialsx [path] [confirm]]`, `/language [code\|auto]` |

### Kit editor

`/kitedit` lists every kit with a button to create one (the name is typed
in chat). `/kitedit <name>` opens a kit: **Contents** is a chest holding the
items (whatever is inside when you close it is the kit), **Cooldown** offers
presets from none to 30 days, once per player, or any duration typed in
chat, plus **Restricted** and **First join** toggles, **Icon** from the held
item, **Rename**, **Preview** and **Delete** (with a confirmation). The
subcommands (`/kitedit <name> cooldown 1d`, ...) still work from the console.

### Permissions with `/perm`

Nodes are granted per player or per group, with wildcards
(`essentialsgg.kit.*`, `essentialsgg.*`, `*`) and `false` to deny. The group
`default` applies to everyone. A player's own entries beat their groups,
the most specific match wins, and group `weight` breaks ties and picks the
prefix. `/perm` entries are checked first, then LuckPerms (if installed),
then the operator level. Only Essentials GG's nodes are affected.

```
/perm group vip create
/perm group vip add essentialsgg.command.fly
/perm group vip prefix <gold>[VIP] 
/perm user Steve group add vip
```

Durations: `30s`, `10m`, `2h`, `7d`, `2w`, `1mo`, `1y`, combined like `1d12h`.

## Permissions

Every node is `essentialsgg.<...>` and has a default operator level used
when no permissions mod decides: 0 for what every player gets, 2-4 for
staff.

- `essentialsgg.command.<name>`: use a command (`essentialsgg.command.home`).
- `essentialsgg.<name>.others`: use it on someone else (`fly`, `god`, `heal`, `feed`, `gmc`..., `spawn`, `homes`, `kit`, `nick`, `balance`, `enderchest`).
- `essentialsgg.homes.limit.<n>`: number of homes (tiers from `homes.limitTiers`), `essentialsgg.homes.limit.unlimited`.
- `essentialsgg.kit.<name>`, `essentialsgg.warp.<name>`: restricted kits and warps.
- `essentialsgg.chat.color`, `.hex`, `.gradient`, `.format`, `.events`: formatting in chat and private messages.
- `essentialsgg.nick.color`, `.hex`, `.gradient`, `.format`, `.impersonate`: formatting in nicknames.
- `essentialsgg.bypass.cooldown`, `essentialsgg.bypass.warmup`, `essentialsgg.tptoggle.bypass`, `essentialsgg.msg.bypass`, `essentialsgg.chat.slowmode.bypass`, `essentialsgg.clearchat.bypass`, `essentialsgg.afk.kickexempt`, `essentialsgg.afk.autoexempt`.
- `essentialsgg.notify.punish`, `essentialsgg.punish.exempt`, `essentialsgg.invsee.modify`, `essentialsgg.enderchest.modify`, `essentialsgg.whois.ip`, `essentialsgg.seen.location`, `essentialsgg.rtp.nether`, `essentialsgg.rtp.end`, `essentialsgg.admin.reload`, `essentialsgg.admin.migrate`.

Chat and tab list prefixes come from `/perm` groups, else from the LuckPerms meta keys
`essentialsgg.prefix` and `essentialsgg.suffix` (`{group_prefix}`,
`{group_suffix}`). On Forge and NeoForge, nodes for restricted kits and
warps are registered at server start; a kit restricted later needs a
restart there.

## Files

| File | What |
|---|---|
| `config/essentialsgg.json` | Module switches and settings (`/ess reload`, or the Mycel config screen) |
| `config/essentialsgg/commands.json` | Per command: `enabled`, `aliases`, `cooldown` (seconds) |
| `config/essentialsgg/lang/<code>.json` | Every message; missing keys fall back to the shipped text. Files you never edited update with the mod; edited ones are kept |
| `<world>/data/essentialsgg/players.json` | Players: homes, balance, nickname, mutes, warnings, settings |
| `<world>/data/essentialsgg/server.json` | Spawn, warps, kits |

The theme colors (`<text>`, `<val>`, `<ok>`, `<err>`, `<warn>`,
`<muted>`, `<accent>`, `<brand>`) are set once in `config/essentialsgg.json`
and usable in every message.

## Moving from EssentialsX

1. Copy `plugins/Essentials` next to the server (or into
   `config/essentialsgg/import/`).
2. `/ess migrate essentialsx` shows what it found, changing nothing.
3. `/ess migrate essentialsx confirm` imports. Players already known keep
   their data; missing homes are added.
4. Bans need no import: copy Paper's `banned-players.json` over, it is the
   same file. `/tempban` writes vanilla bans with an end date.

## Building

JDK 25, then:

```sh
./gradlew build              # all loaders + tests
./gradlew build -Pmc=26.2    # another Minecraft line (see mc/)
./gradlew :common:test       # unit tests only
```
