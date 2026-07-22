# Bankstand RuneLite plugin

The RuneLite companion for [Bankstand](https://bankstand.christiaanvaneijnsbergen.nl). It pairs your
client with your Bankstand account and proves, per character, that a logged-in client is connected,
which shows as a "RuneLite verified" badge on your account. You generate a short code on the website,
paste it into the plugin panel, and the plugin exchanges it for a long-lived device token stored
locally; from then on it submits your account identity on login.

Licence: **BSD 2-Clause** (see `LICENSE`). Runtime dependencies: **none** beyond what `runelite-client`
already ships (Gson and OkHttp are used as its transitives).

## What it does

- A "Bankstand" side panel: paste a pairing code and Pair. The server URL lives under a collapsed
  **Advanced** toggle (defaults to the live site, override it for local testing) so the whole flow is
  in one place.
- Exchanges the code against `POST {serverUrl}/api/plugin/v1/pair` and stores the returned device
  token via RuneLite's `ConfigManager` (local, never logged).
- On login, submits the current character's account hash and display name to
  `POST {serverUrl}/api/plugin/v1/submit` (once per session, authenticated by the device token). If
  that character is one you have tracked in Bankstand, it binds and the panel shows **RuneLite
  verified as `<name>`**; otherwise it shows connected but not tracked.
- Shows connection status and a Disconnect button (clears the local token; to revoke it server-side,
  use Bankstand > Account).
- Tracks the logged-in account hash per character, never carries state across an account switch, and
  never submits the logged-out sentinel (`-1`).

No gameplay data (skills, quests, collection log) is captured yet; that is planned for later releases.

## Configuration

- **Server URL** (panel > Advanced): defaults to the live Bankstand site. Set it to
  `http://localhost:3000` (or whatever port your `pnpm dev` uses) to pair and submit against a local
  server. Stored under the `bankstand.serverBaseUrl` config key.

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
   locally and set the panel's Advanced Server URL to your local address and port).
2. In Bankstand, sign in and go to **Account > Connect RuneLite**, generate a pairing code.
3. In RuneLite, open the **Bankstand** panel (the gold logo in the sidebar), paste the code, Pair.
   The panel flips to **Connected**.
4. Log in as a character you have **tracked** in Bankstand. A tick after login the plugin submits, and
   the panel shows **RuneLite verified**; the badge appears on that account in Bankstand.

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

## Layout

- `src/main/java/com/bankstand/PairingCodes.java` normalizes and validates the code (mirrors the
  server exactly).
- `src/main/java/com/bankstand/BankstandClient.java` performs the pairing exchange and the identity
  submit behind an `HttpTransport` seam, so the logic is unit-tested with no real socket.
- `src/main/java/com/bankstand/http/OkHttpTransport.java` is the thin real transport over RuneLite's
  OkHttpClient.
- `src/main/java/com/bankstand/session/AccountSession.java` tracks the account hash across logins and
  whether identity has been submitted this session.
- `src/main/java/com/bankstand/dto/` holds the `PairResponse` / `SubmitResponse` DTOs.
- `src/main/java/com/bankstand/BankstandPlugin.java` / `BankstandPanel.java` wire it into RuneLite.
- Tests are under `src/test/java/com/bankstand/` and need only JUnit.
