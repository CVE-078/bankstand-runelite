# Bankstand RuneLite plugin

Keeps your progress on [Bankstand](https://bankstand.gg) up to date while you play. Everything it
sends is private to your own account. Nothing is public or ranked.

## Pairing

1. Sign in at [bankstand.gg](https://bankstand.gg), open
   [Plugin connection](https://bankstand.gg/account/connection) and generate a pairing code.
2. In RuneLite, open **Settings > Bankstand** and paste the code into **Pairing code**.
3. Log in. The chat box shows **Verified as `<name>`**.

One pairing per RuneLite install syncs every character you have claimed on Bankstand. A character
you have not claimed yet is not synced, and the chat box says so.

## What it sends

Each item has its own toggle. Only **Skill XP** is on by default.

| Toggle | What it sends |
|---|---|
| Skill XP | XP per skill, your account hash and display name. This links your character, and carries the regular progress update. |
| Quest progress | Each quest's state: not started, started or finished. |
| Diary progress | Completed tasks per tier, reward claims, and the game's per-task diary values. |
| Combat achievements | Completed counts per tier and boss, and task names the game announces in chat. |
| Collection log | Filled slots and new unlocks. Open the log and click **Search** to read it all at once. |
| Account type | Main, ironman or a group type. |
| Notable drops | Rare or valuable drops (1,000,000 gp by default): item, value and monster. |
| Pet drops | Which pet you got and when. |
| Session log | When you log in and out, so the site can show how long you played. |
| Loot log | Items from monster kills, about once a minute. |
| Slayer tracker | Your current task, master, points and streaks, and finished tasks. |

Progress is checked every 60 seconds and sent only when something changed. Right after login,
diaries, account type and Slayer task can wait a few minutes for the game to load them fully, so
an early read never lowers your saved progress.

Only regular worlds are read. Nothing is read on Leagues, Deadman, tournament, PvP Arena, beta,
quest speedrunning, Fresh Start or Last Man Standing worlds, inside a Last Man Standing game, or
before the welcome screen is closed.

## What it never sends

- Your bank, equipment, inventory, location or world.
- Anything about other players, including loot from player kills.
- Anything typed in chat. It only reads the game's own messages.

It never logs your pairing code, device token or account hash. It only observes the client: it
never clicks, sends input or runs game scripts.

## Settings and panel

- **Server URL**: leave it unless you run Bankstand yourself. The token is only sent to the server
  you paired with, and redirects are never followed.
- **Disconnect**: forgets this device and revokes it on the server.
- The sidebar panel shows the connection, when each toggle last sent something, and recent
  activity. **Sync now** sends straight away.
- Chat commands: `::bstand` (status), `::bstand sync`, `::bstand link`, `::bstand log`,
  `::bstand repair` and `::bstand help`. `::stand` works too.

The pairing is stored in `<RUNELITE_DIR>/bankstand/device.json`, outside RuneLite's config, so
config sync never uploads it.

## Building

Requires JDK 11 or later. `./gradlew build` compiles the plugin and runs the tests.

## Reporting a problem

[Open an issue](https://github.com/CVE-078/bankstand-runelite/issues/new/choose) with the plugin
version and what the chat box said. Never paste your pairing code or device token.

## Licence

BSD 2-Clause, see [`LICENSE`](LICENSE).

Bankstand is an independent project and is not affiliated with, endorsed by or sponsored by Jagex
Ltd or the RuneLite project. Old School RuneScape is a trademark of Jagex Ltd.
