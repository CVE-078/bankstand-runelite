# Bankstand RuneLite plugin

The RuneLite companion for [Bankstand](https://bankstand.christiaanvaneijnsbergen.nl). It pairs your
client with your Bankstand account and proves, per character, that a logged-in client is connected,
which shows as a "RuneLite verified" badge on your account. You generate a short code on the website,
paste it into the plugin's settings, and the plugin exchanges it for a long-lived device token stored
locally; from then on it submits your account identity on login.

Licence: **BSD 2-Clause** (see `LICENSE`). Runtime dependencies: **none** beyond what `runelite-client`
already ships (Gson and OkHttp are used as its transitives).

## What it does

- Lives entirely in RuneLite's own settings screen, with no sidebar panel. Paste a pairing code into
  **Pairing code** and it pairs.
- Exchanges the code against `POST {serverUrl}/api/plugin/v1/pair` and stores the returned device
  token via RuneLite's `ConfigManager` (local, never logged).
- On login, submits the current character's account hash and display name to
  `POST {serverUrl}/api/plugin/v1/submit` (once per session, authenticated by the device token). If
  that character is one you have tracked in Bankstand, it binds and the plugin says **Verified as
  `<name>`** in chat; otherwise it says the character is not claimed yet.
- Every 60 seconds while logged in, captures skill XP and (behind their own opt-ins) quest and
  achievement diary state, submitting only when something changed since the last acknowledged submit.
  An idle account sends nothing, and a submission carries only the capabilities that actually moved:
  an XP gain sends skill XP alone, not your whole collection log along with it.
- Reads your collection log (opt-in) while the log interface enumerates its items, guided by you
  rather than automated. Unlike the others this ACCUMULATES: a partial read adds to what is known
  and never replaces it, because the game only reveals the log while you are looking at it.
- Takes one last capture as you log out, so the final minute of a session is not dropped by the 60s
  schedule. The read is checked before it is sent: a cleared client reads as zeroes, and sending
  those would look like losing XP. A rejected read costs nothing, since the next login re-reads live.
- Reports how many log SLOTS you have filled, not how many item ids it holds. Some slots are awarded
  by two sources under two different ids (Volcanic Mine's Prospector pieces are not Motherlode
  Mine's), so eight items can fill four slots. Submissions still carry the raw ids: which item you
  hold is a fact, and the server maps ids to slots itself.
- Remembers what the server already accepted, per character, in
  `<RUNELITE_DIR>/bankstand/acked-state.json`, so restarting the client does not re-send everything.
  Delete the file and the next capture simply sends it all once.
- Tracks the logged-in account hash per character, never carries state across an account switch, and
  never submits the logged-out sentinel (`-1`).
- Skips non-standard worlds (tournament, seasonal, deadman, PvP arena), whose XP is not the account's
  real progression.

Everything captured is private to your own Bankstand account. None of it is public or ranked.

### What it does not capture

Stated because a gap is easy to mistake for a bug, and because a figure nobody observed is not the
same as zero.

- **Combat achievements.** No capture path exists yet, so Bankstand shows the unavailable placeholder
  for them rather than a count.
- **Individual diary tasks.** Diary capture is tier-level: a tier reads as complete or not, so a tier
  at 21 of 22 tasks currently reports nothing.
- **Bank value, worn equipment, inventory, chat and your location.** Not captured, not offered, and
  not requestable by the server.

## Configuration

All of it is under **RuneLite settings > Bankstand**, in two sections.

The split matters: **Connection** is which Bankstand account this client is tied to, and **Collect**
is what this client reads and sends. Who may then *see* any of it is a third question, answered in
your Bankstand privacy settings and deliberately not here. A setting in a game client cannot be the
source of truth for a server-side audience, which is why nothing in this plugin is called "share".

### Connection

- **Server URL**: defaults to the live Bankstand site. Set it to `http://localhost:3000` (or whatever
  port your `pnpm dev` uses) to pair and submit against a local server. Stored under the
  `bankstand.serverBaseUrl` config key. **A stale value here makes every submit fail**, which is why
  it is a visible setting rather than hidden behind an advanced toggle.
- **Pairing code**: paste a code to pair. Cleared automatically once used, successfully or not.
- **Disconnect**: tick to forget this device's token. Unticks itself. To revoke server-side, use
  Bankstand > Account.

### Collect

Every toggle names what it sends. Everything is private to your own Bankstand account by default.

- **Collect skill XP**: default **on**. Sends your XP per skill, your account hash and your display
  name. It gates the whole capture rather than just its own block, because the v1 envelope makes
  `skills` required and the rest optional riders on it, so with this off there is no submission for
  them to attach to and a paired client goes quiet.
- **Collect quest progress** / **Collect achievement diary progress**: opt-in, both default off.
  Diaries are tier level only.
- **Collect collection log**: opt-in, default off. The collection log is not held in the
  client, so it is read while the log interface enumerates, which is what searching your
  log makes it do. **Open your collection log and click Search. That is the whole flow**,
  and an infobox counts entries as they arrive. There is no button and no menu entry: the
  plugin cannot drive the interface itself (see the automation invariant below), which is
  why the click is yours, and adding a right-click entry only put "Sync to Bankstand" on
  every click in the game. If another plugin already makes your log enumerate Bankstand
  picks that up too, and ordinary page browsing keeps adding to what is known.

These were called `shareQuests`, `shareDiaries` and `shareCollectionLog` before. They were renamed
with no migration, so a client that paired earlier keeps its pairing and its server URL but reverts
these three opt-ins to off. Re-tick them once.

Outcomes are reported in the chat box, since there is no panel to hold a status line. A failure that
repeats every capture cycle is announced once, not every 60 seconds, and the recovery is announced
when it clears.

A client that keeps failing also slows down, skipping captures on a doubling count up to about 16
minutes, and resuming the moment one succeeds. **A rejected or revoked token stops it entirely**:
retrying cannot fix that, so the plugin says so once and sends nothing until you re-pair.

## Build and run (developer mode)

Requires JDK 11+ (17 works).

```
./gradlew build      # compile + run the JUnit tests
./gradlew run        # launch a developer-mode RuneLite with this plugin sideloaded
```

`./gradlew run` starts RuneLite in developer mode with the plugin already loaded, so you can iterate
without installing anything.

## Pair and verify

1. Make sure the Bankstand pairing + submit endpoints are reachable (deploy the server, or run it
   locally and set **Server URL** to your local address and port).
2. In Bankstand, sign in and go to **Account > Connect RuneLite**, generate a pairing code.
3. In RuneLite, open **settings > Bankstand**, paste the code into **Pairing code**. The field clears
   and the chat box confirms the connection.
4. Log in as a character you have **tracked** in Bankstand. A tick after login the plugin submits, and
   the chat box shows **Verified as `<name>`**; the badge appears on that account in Bankstand.

## Testing with a Jagex account

A from-source client (`./gradlew run`) cannot log a Jagex account in on its own, Jagex accounts need a
Launcher session. One-time setup so the dev client can reuse your session:

1. Set the RuneLite launcher client argument `--insecure-write-credentials` (launcher 2.6.3+). On
   macOS: `/Applications/RuneLite.app/Contents/MacOS/RuneLite --configure`, then add it to the
   **Client arguments** box. This also applies when launching via the Jagex Launcher.
2. Launch RuneLite once via the **Jagex Launcher** and log in. It writes
   `~/.runelite/credentials.properties`.
3. Run `./gradlew run`. It reuses that session, so you can log in and test.
4. When done, delete `~/.runelite/credentials.properties` (it stores your session in plaintext), and
   you can hit **End sessions** on runescape.com to invalidate it.

## Contributing

Development rules, the invariants behind the design, and the module layout are
maintained in the Bankstand project repository rather than here. If you are
working on this plugin and need them, ask.

Two constraints are worth stating up front because breaking either fails review
rather than a test:

- **The plugin observes the client. It never drives it.** `client.menuAction`,
  `client.runScript` and `client.invokeMenuAction` are banned, and
  `NoAutomationApiTest` fails the build if one reappears. Hub PR #11371 was
  closed over exactly this.
- **Never log the device token, the account hash, the display name, or a raw
  request body.** The first is a credential; the rest identify a real account.

`./gradlew build` is the merge gate and runs on every pull request.

## Licence

BSD 2-Clause. See `LICENSE`.

Bankstand is an independent project and is not affiliated with, endorsed by or
sponsored by Jagex Ltd or the RuneLite project. Old School RuneScape is a
trademark of Jagex Ltd.
