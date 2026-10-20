# Bankstand RuneLite plugin

The RuneLite companion for [Bankstand](https://bankstand.gg). It links your client to your Bankstand
account and keeps your progress up to date while you play. Everything it sends is private to your
own account; who else can see it is a separate setting on the website.

## Pairing

1. On [bankstand.gg](https://bankstand.gg), open **Account > Connect RuneLite** and generate a code.
2. In RuneLite, paste it into **Settings > Bankstand > Pairing code**. The chat box confirms it.
3. Log in as a character you track on Bankstand. The chat box shows **Verified as `<name>`**.

Each machine pairs separately and can be revoked on its own from the website.

## What it sends

Each capability has its own toggle under **Settings > Bankstand**. Only Skill XP is on by default,
and turning it off stops everything.

| Toggle | What it sends |
|---|---|
| Skill XP | XP per skill, your account hash and display name, when they change |
| Quest progress | Each quest's state (not started, started, finished) |
| Diary progress | Completed tasks per diary tier, and when a task completes |
| Combat achievements | Completed counts per tier and boss, and task names the game announces |
| Collection log | Filled slots and new unlocks; open the log and click **Search** for a full read |
| Account type | Main, ironman or group type |
| Notable drops | Unique, untradeable or valuable drops (1,000,000 gp by default) |
| Pet drops | Which pet and when |
| Session log | Log in and out, and a once-a-minute online mark (memory only, never on disk) |
| Loot log | Item ids and quantities from monster kills, with the monster's name |
| Slayer tracker | Your current task (creature, amount, master, points and streaks), when you got it, and when you finish one |

Nothing is read on Leagues, Deadman, tournament, PvP Arena, beta, no-save or speedrunning worlds.

## What it never captures

- Bank value, worn equipment, inventory, location, world or idle state.
- Anything about other players, including loot from player kills.
- Anything typed in chat. It only reads the game's own messages.
- The game's kill counters, which specific diary task you did, and your slayer location, block list
  or unlocks.
- Loot from chests and reward screens (Barrows, raids, clue caskets).

It never logs your pairing code, device token, account hash or display name. It only observes the
client: no clicks, no input, no game scripts.

## Using it

- **Sidebar panel:** sync status, when each capability last sent, recent activity, **Sync now**.
- **Chat:** `::bstand` shows status; `::bstand help` lists the rest (sync, re-link, full log read,
  re-pair, export settings).
- **Server URL:** leave it on the default unless you run Bankstand yourself.
- The pairing lives in `<RUNELITE_DIR>/bankstand/device.json`, outside RuneLite's config, so config
  sync never uploads it. The Slayer task start is kept beside it so a restart does not lose it.
  Failures are reported once in chat and retried with a back-off.

## Building

JDK 11 or later: `./gradlew build` compiles and runs the tests, the same as CI.

## Reporting a problem

[Open an issue](https://github.com/CVE-078/bankstand-runelite/issues/new/choose) with the plugin
version and what the chat box said. Never paste your pairing code or device token.

## Licence

BSD 2-Clause, see [`LICENSE`](LICENSE). No runtime dependencies beyond `runelite-client`.

Bankstand is independent and not affiliated with, endorsed or sponsored by Jagex Ltd or the
RuneLite project. Old School RuneScape is a trademark of Jagex Ltd.
