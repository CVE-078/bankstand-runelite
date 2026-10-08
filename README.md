# Bankstand RuneLite plugin

The RuneLite companion for [Bankstand](https://bankstand.gg).

## What it does

- Links your RuneLite client to your Bankstand account.
- Keeps your progress on the site up to date while you play.
- Gives your linked character a "RuneLite verified" badge.
- Everything it sends is private to your own account. Nothing is public or ranked. Who can see it is
  a separate setting on the website.

## Pairing

1. Sign in at [bankstand.gg](https://bankstand.gg) and open **Account > Connect RuneLite**.
   Generate a pairing code.
2. In RuneLite, open **Settings > Bankstand** and paste the code into **Pairing code**. The field
   clears itself and the chat box confirms the connection.
3. Log in as a character you track on Bankstand. The chat box shows **Verified as `<name>`**.

If the character is not one you track, the plugin tells you instead of linking it.

Each machine pairs separately and shows up as its own device on Bankstand, with its own revoke
button.

## What each toggle captures

Each capability has its own toggle and its own section under **Settings > Bankstand**.

- **Skill XP** (on by default). Your XP per skill, your account hash and your display name. Checked
  every 60 seconds and sent only when something changed. This toggle gates all the others: with it
  off, nothing is sent.
- **Quest progress** (opt-in). Each quest's state: not started, started or finished.
- **Diary progress** (opt-in). A completed task count per diary tier, whether the tier reads
  complete, and the moment a task completes, from the game's chat line.
- **Combat achievements** (opt-in). A completed count per tier and per boss (where the game has a
  counter), the task name when the game announces a completion in chat, and tier completions. Tasks
  done before you turned this on are counted but not named.
- **Collection log** (opt-in). Which slots you have filled, plus each new unlock as the game
  announces it. Browsing the log adds what you see. Open it and click **Search** to read the whole
  log at once. A partial read never removes anything.
- **Account type** (opt-in). Whether the account is a main, an ironman or a group type. The hiscores
  cannot show Group Ironman, so this fills the gap. Your own answer on the site still wins.
- **Notable drops** (opt-in). Unique, untradeable or high-value drops: the item, its value and the
  source. A tradeable drop counts once it clears your gp threshold (1,000,000 by default).
  Untradeable items are judged by name.
- **Pet drops** (opt-in). Which pet you got and when.

It also takes one last capture when you log out. A cleared client reads as zeroes, and that read is
dropped rather than sent.

## What it never captures

- Which specific diary task you completed. Only the moment, tier and area.
- Bank value, worn equipment, inventory or your location.
- Anything you or other players type in chat. It only reads the game's own messages, for the
  capabilities above.

It never logs your pairing code, device token, account hash or display name. It only observes the
client: it never clicks, sends input or runs game scripts.

## Configuration

Under **Settings > Bankstand**:

- **Server URL.** Defaults to the live site. Leave it unless you run Bankstand yourself.
- **Pairing code.** Paste a code to pair. It clears itself once used.
- **Disconnect.** Tick to forget this device's token. To revoke it on the server, use **Account**
  on the website.
- **One section per capability**, each with its own toggle. Notable drops also has its gp
  threshold.

The pairing is stored in `<RUNELITE_DIR>/bankstand/device.json`, not in RuneLite's config, so
config sync never uploads it.

### Sidebar panel

Click the Bankstand icon in the sidebar.

- **Status dot.** Green: last sync worked. Amber: last attempt failed. Grey: not paired or nothing
  sent yet.
- **Capabilities.** When each enabled capability last sent something. A dash means nothing new to
  send yet.
- **Recent activity.** Unlocks, achievements, diary tasks, drops and pets sent this session.
- **Sync now** sends immediately. **Open Bankstand** opens the site.

### Chat commands

`::stand` works as a shorthand for `::bstand`.

- `::bstand`: connection status and what was last sent.
- `::bstand sync`: send now.
- `::bstand link`: re-link this character.
- `::bstand log`: start a full collection log read. Needs the log open.
- `::bstand repair`: clear a stale or revoked pairing, then paste a new code.
- `::bstand export`: print and copy your toggle settings. Never includes the code, token or URL.
- `::bstand help`: list the commands.

### When something fails

- The chat box and the panel say what went wrong. A repeated failure is reported once.
- Repeated failures back off to about 16 minutes and recover on the next success.
- A rejected or revoked token stops sending until you pair again.

## Building from source

Requires JDK 11 or later.

```
./gradlew build
```

This compiles the plugin and runs the tests, the same as CI.

## Reporting a problem

[Open an issue](https://github.com/CVE-078/bankstand-runelite/issues/new/choose) with the plugin
version and what the chat box said. Never paste your pairing code or device token.

## Licence

BSD 2-Clause. See [`LICENSE`](LICENSE). No runtime dependencies beyond what `runelite-client`
ships.

Bankstand is an independent project and is not affiliated with, endorsed by or sponsored by Jagex
Ltd or the RuneLite project. Old School RuneScape is a trademark of Jagex Ltd.
