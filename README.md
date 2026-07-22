# Bankstand RuneLite plugin

The RuneLite companion for [Bankstand](https://bankstand.christiaanvaneijnsbergen.nl). This first
release does one thing: it pairs your client with your Bankstand account. You generate a short code
on the website, paste it into the plugin panel, and the plugin exchanges it for a long-lived device
token that it stores locally. No gameplay data is read or uploaded.

Licence: **BSD 2-Clause** (see `LICENSE`). Runtime dependencies: **none** beyond what `runelite-client`
already ships (Gson and OkHttp are used as its transitives).

## What it does (this release)

- A "Bankstand" side panel with a pairing-code field and a Pair button.
- Exchanges the code against `POST {serverUrl}/api/plugin/v1/pair` and stores the returned device
  token via RuneLite's `ConfigManager` (local, not logged).
- Shows connection status and a Disconnect button (which clears the local token; to revoke it
  server-side, use Bankstand > Account).
- Tracks the logged-in account hash so a future data-capture release can never carry one character's
  state into another's session. The logged-out sentinel (`-1`) is never used.

It does **not** submit any gameplay data, bind your character, or show a verified badge yet. Those
land in later releases.

## Configuration

- **Server URL** (plugin config): defaults to the live Bankstand site. Set it to `http://localhost:3000`
  to pair against a local `pnpm dev` server.

## Build and run (developer mode)

Requires JDK 11+ (17 works).

```
./gradlew build      # compile + run the JUnit tests
./gradlew run        # launch a developer-mode RuneLite with this plugin sideloaded
```

`./gradlew run` starts RuneLite in developer mode with the plugin already loaded, so you can iterate
without installing anything.

## Pair it

1. Make sure the Bankstand pairing endpoint is live (deploy the server, or run it locally and set the
   plugin's Server URL to your local address).
2. In Bankstand, sign in and go to **Account > Connect RuneLite**, generate a pairing code.
3. In RuneLite, open the **Bankstand** panel, paste the code, and click **Pair**.
4. The panel flips to **Connected**.

## Layout

- `src/main/java/com/bankstand/PairingCodes.java` normalizes and validates the code (mirrors the
  server exactly).
- `src/main/java/com/bankstand/BankstandClient.java` performs the exchange behind an `HttpTransport`
  seam, so the pairing logic is unit-tested with no real socket.
- `src/main/java/com/bankstand/http/OkHttpTransport.java` is the thin real transport over RuneLite's
  OkHttpClient.
- `src/main/java/com/bankstand/session/AccountSession.java` tracks the account hash across logins.
- `src/main/java/com/bankstand/BankstandPlugin.java` / `BankstandPanel.java` wire it into RuneLite.
- Tests are under `src/test/java/com/bankstand/` and need only JUnit.
