# Bankstand RuneLite plugin

The RuneLite companion for [Bankstand](https://bankstand.gg).

## What it does

- Links your RuneLite client to your Bankstand account.
- Keeps your progress on the site up to date while you play.
- Gives your linked character a "RuneLite verified" badge.
- Everything it sends is private to your own account. Nothing is public or ranked. Who can see it is
  a separate setting on the website.

## Pairing

1. Sign in at [bankstand.gg](https://bankstand.gg) and open **Plugin connection**
   ([bankstand.gg/account/connection](https://bankstand.gg/account/connection)). Generate a pairing
   code.
2. In RuneLite, open **Settings > Bankstand** and paste the code into **Pairing code**. The field
   clears itself and the chat box confirms the connection.
3. Log in as a character you track on Bankstand. The chat box shows **Verified as `<name>`**.

If the character is not one you track, the plugin tells you instead of linking it.

Each machine pairs separately and shows up as its own device on Bankstand, with its own revoke
button.

## What each toggle captures

Each capability has its own toggle and its own section under **Settings > Bankstand**.

- **Skill XP** (on by default). Your XP per skill, your account hash and your display name. Checked
  every 60 seconds and sent only when something changed. This is what links your character to
  Bankstand, so your other captures only reach your account once it has linked. With it off, the
  regular update stops, and with it your quest, diary, combat achievement, collection log, account
  type and Slayer task progress. Drops, pets, loot, sessions and chat-announced completions still
  send if their own toggles are on. To stop everything, use **Disconnect**.
- **Quest progress** (opt-in). Each quest's state: not started, started or finished. For a quest
  too new for RuneLite's quest list, the quest name from the quest-complete scroll.
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
  monster. A tradeable drop counts once it clears your gp threshold (1,000,000 by default).
  Untradeable items are judged by name.
- **Pet drops** (opt-in). Which pet you got and when.
- **Session log** (opt-in). When you log in and out, plus an online mark once a minute on a
  regular world, so the site can show how long you played. A world hop is not a logout. Kept in
  memory only, never written to disk.
- **Loot log** (opt-in). Every item you get from killing a monster, as item ids and quantities,
  with the monster's name and how many kills dropped loot. Sent about once a minute. Separate
  from notable drops. It does not turn on the Session log; the sidebar panel asks first.
- **Slayer tracker** (opt-in). Your current task (creature, amount left and assigned, the master,
  points, and your standard, Wilderness and Mortimer streaks) or that you have none, and when you
  finish one, from the game's completion message. Also when you got the task, if the plugin was
  running to see it; kept in a local file so a restart does not lose it.

It also takes one last capture when you log out. A cleared client reads as zeroes, and that read is
dropped rather than sent.

Only regular worlds are read: free, members, PvP, Bounty, high-risk and total-level worlds. Nothing
is read or sent on any other world type (Leagues, Deadman, tournament, PvP Arena, beta, no-save,
quest speedrunning, Fresh Start, Last Man Standing, or a mode added after this version), inside a
Last Man Standing game, or before the welcome screen is closed after login.

## What it never captures

- Which specific diary task you completed. Only the moment, tier and area.
- Bank value, worn equipment, inventory or your location.
- Anything about other players. Loot from a player kill is never captured.
- The game's own kill counters. The Loot log only counts kills that dropped something.
- Which world you are on, or whether you are idle.
- Your slayer task's location, block list or unlocks.
- Loot from chests and reward screens (Barrows, raids, clue caskets). These are not monster kills.
- Anything you or other players type in chat. It only reads the game's own messages, for the
  capabilities above.

It never logs your pairing code, device token, account hash or display name. It only observes the
client: it never clicks, sends input or runs game scripts.

## Configuration

Under **Settings > Bankstand**:

- **Server URL.** Defaults to the live site. Leave it unless you run Bankstand yourself. A pairing
  belongs to the server it was made with: point this at another server and nothing is sent until
  you pair with that one. Redirects are never followed.
- **Pairing code.** Paste a code to pair. It clears itself once used.
- **Disconnect.** Tick to forget this device's token and revoke it on the server. If the server
  cannot be reached, a chat message says so; remove the device on **Plugin connection**
  ([bankstand.gg/account/connection](https://bankstand.gg/account/connection)).
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
- **Also turn on the Session log?** Shown while the Loot log is on and the Session log is off.
  Nothing changes unless you press its button.
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
version (shown next to Bankstand in RuneLite's Plugin Hub list) and what the chat box said. Never paste your pairing code or device token.

## Licence

BSD 2-Clause. See [`LICENSE`](LICENSE). No runtime dependencies beyond what `runelite-client`
ships.

Bankstand is an independent project and is not affiliated with, endorsed by or sponsored by Jagex
Ltd or the RuneLite project. Old School RuneScape is a trademark of Jagex Ltd.
