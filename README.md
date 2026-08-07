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

## Conventions and invariants

Constraints that a fresh reader will not infer from the code, and that break something real if
ignored.

- **`./gradlew build` is the merge gate**, and CI now runs the same build on every pull request and
  on pushes to `main`. A green local build before every commit is still the fast path. One class:
  `./gradlew test --tests com.bankstand.<Class>`.
- **A capability is sent whole or not at all, never as a delta within a block.** The server merges
  blocks with jsonb `||`, a top-level replace, so a block carrying only its changed fields would
  erase every field it left out. `plan` therefore decides *whether* to include a block and never
  *what* to put in one, and `SubmitEnvelope` has no way to express a partial block, so the unsafe
  granularity is unrepresentable rather than merely discouraged. Omitting an unchanged block is safe
  and does not weaken the per-block acknowledgement: a block the server never acknowledged has no
  baseline to match, reads as changed, and keeps going out until it is stored.
- **Captured game data never goes through `ConfigManager`.** `saveConfiguration` checks
  `ConfigProfile.isSync()` and, when the player has sync on, PATCHes the profile's whole changed key
  set to RuneLite's own config service. There is no per-key exclusion, so a plugin cannot mark one
  value local-only. Anything the plugin reads out of the game is the player's data and goes to
  Bankstand alone, so it lives in `<RUNELITE_DIR>/bankstand/`, not in config. (The device token is
  already in config and predates this rule; that is a known open question, not a precedent.)
- **A baseline and the state it measures must have the same lifetime.** `CollectionLogBaseline` is a
  count, valid as a change gate only because the observed set grows monotonically. Persist the count
  while the accumulator resets and the next partial browse churns a short block every session;
  persist the set but derive the count from it and a log whose submit failed is treated as delivered
  and never sent again. They are written and restored together, and `AckedStateRestartTest` pins
  both failure modes.
- **The plugin observes the client. It never drives it.** Hub PR #11371 was closed with "use of
  `client.menuAction` is not allowed", which is why the collection log read is guided: the player
  clicks the game's own Search and the plugin watches script `4100`, which fires for every entry
  whoever triggered it. The automated version was two lines and looked entirely reasonable, so
  `NoAutomationApiTest` scans the source and fails the build if `client.menuAction`,
  `client.runScript` or `client.invokeMenuAction` reappears outside a comment. `MenuAction.RUNELITE`
  is fine and is not what the ban is about: it types a menu entry the player chooses to click.
  Reintroducing a driving call costs a rejected Hub submission and another round of review latency.
- **Never log the device token, the account hash, the display name, or a raw request body.** The token
  is a credential; the other three identify a real person's account. The raw token is returned once by
  the pairing exchange and stored, never printed.
- **The contract fixtures mirror the server's.** `src/test/resources/contracts/*.json` correspond to
  bankstand's `lib/plugin/contracts/*.json`, and the wire shape is fixed by the server. The rule is
  **equivalence of the parsed JSON, not of the bytes**: `SubmitEnvelopeContractTest` parses both sides
  with Gson and compares `JsonObject`s, so indentation and line endings are free (a Windows checkout
  gets CRLF either way). Change one side without the other and the two stop agreeing about the wire.
- **Zero third-party runtime dependencies.** Only `net.runelite:client` (compileOnly) and its
  transitives, which is where Gson and OkHttp come from. JUnit is test-scope. Do not add a JSON, UUID
  or HTTP library: `UuidV7` exists precisely so there is no dependency for it, and the Plugin Hub
  reviews a `build=standard` plugin faster than one with custom dependencies to hash-verify.
- **Capture reads run on the client thread.** `Quest.getState(Client)` and `getSkillExperience` read
  varps and varbits and must not be called off it. Read every capability inside the one
  `clientThread.invoke(...)` block so a submission is a single consistent snapshot.
- **Only the main game counts.** Tournament, seasonal, deadman and PvP-arena worlds are skipped: their
  XP is not the account's real progression.
- **No em dashes** anywhere in code or comments. Use a comma, period, or parentheses.
- **Comments** document the timeless why. No issue numbers, dates, or spec paths.
- **Commits:** conventional `type(scope): subject`, subject-only, no body, no AI-attribution trailer.
  The PR title (the squash subject) is a plain imperative sentence.

## Layout

- `src/main/java/com/bankstand/PairingCodes.java` normalizes and validates the code (mirrors the
  server exactly).
- `src/main/java/com/bankstand/BankstandClient.java` performs the pairing exchange and the snapshot
  and identity submits behind an `HttpTransport` seam, so the logic is unit-tested with no real
  socket.
- `src/main/java/com/bankstand/http/` holds the transport seam (`HttpTransport`, `HttpResponse`) and
  `OkHttpTransport`, the thin real transport over RuneLite's OkHttpClient.
- `src/main/java/com/bankstand/session/AccountSession.java` tracks the account hash across logins and
  whether identity has been submitted this session.
- `src/main/java/com/bankstand/dto/` holds the `PairResponse`, `SubmitResponse` and
  `SubmitSnapshotResponse` DTOs.
- `src/main/java/com/bankstand/SubmitEnvelope.java` builds the v1 wire envelope, and
  `src/main/java/com/bankstand/UuidV7.java` generates its time-ordered submission ids without a
  dependency.
- `src/main/java/com/bankstand/BankstandConfig.java` is the whole UI: a RuneLite config interface,
  where two of the items (pairing code, disconnect) are actions rather than settings.
- `src/main/java/com/bankstand/BankstandKeys.java` holds the storage keys and the server-URL
  normalisation both sides share. The device token is deliberately not a config item.
- `src/main/java/com/bankstand/NoticeGate.java` decides whether a submit outcome is worth a chat line,
  so a recurring failure is announced once rather than every capture cycle.
- `SkillBaseline`, `QuestBaseline`, `DiaryBaseline` and `CollectionLogBaseline` are the change gates:
  each remembers the last acknowledged state for its capability so an unchanged capability is not
  resubmitted. A capability's baseline only advances when the server acknowledges that block.
- `src/main/java/com/bankstand/CollectionLogAccumulator.java` accumulates observed item ids rather
  than replacing them, because the log is not resident in the client and an enumeration can be
  partial. It cannot report absence, which is why the server treats an omitted item as "not observed"
  and never as "does not have it".
- `src/main/java/com/bankstand/DiaryVarbits.java` maps diary wire keys to the varbit carrying each
  tier's completion flag. All 48 tiers are captured, Karamja included. The game keeps three varbits
  per tier (`*_COMPLETE`, `*_REWARD`, `*_COUNT`) and this table reads only the first, so a tier that
  is complete but unclaimed cannot be distinguished here.
- `PairingException` and `SubmitException` carry the terminal-versus-retryable distinction.
- `src/main/java/com/bankstand/BankstandPlugin.java` wires it into RuneLite.
- Tests are under `src/test/java/com/bankstand/` and need only JUnit. Fixtures live in
  `src/test/resources/contracts/`.

---

_Last verified against `main` on 2026-08-07. Update this anchor when the plugin's behaviour changes._
