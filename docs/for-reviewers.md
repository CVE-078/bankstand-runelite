# For Hub reviewers

A short, grep-verifiable answer to the two questions every submission gets: where does data leave
the client, and does anything here automate the game. Every claim below is something you can check
yourself in under a minute; the commands are included.

## Where the network calls actually are

Exactly one file opens a socket:

```
$ grep -rn "newCall(" src/main/java
src/main/java/com/bankstand/http/OkHttpTransport.java:33:    try (Response response = client.newCall(builder.build()).execute()) {
src/main/java/com/bankstand/http/OkHttpTransport.java:44:    try (Response response = client.newCall(builder.build()).execute()) {
```

`OkHttpTransport` is a thin wrapper over RuneLite's own injected `OkHttpClient`, called only from
`BankstandClient`, which builds the request bodies and is the only class that constructs a URL from
the player's configured server address:

```
$ grep -rln "HttpTransport\b" src/main/java
src/main/java/com/bankstand/BankstandClient.java
src/main/java/com/bankstand/BankstandPlugin.java
src/main/java/com/bankstand/http/HttpTransport.java
src/main/java/com/bankstand/http/OkHttpTransport.java
```

`HttpTransport` is a plain interface (`post`/`get`, no implementation) so `BankstandClient`'s
pairing and submit logic can be unit-tested against a fake, with no real socket and no extra test
dependency. `BankstandPlugin` only wires the two together at startup; it makes no calls itself.

That is the entire network surface: two files that call out, one interface between them, one
caller. Nothing else in the source tree references `OkHttpClient`, a raw `Socket`, or any HTTP
library:

```
$ grep -rln "OkHttpClient\|java.net.Socket\|HttpURLConnection" src/main/java
src/main/java/com/bankstand/BankstandClient.java
src/main/java/com/bankstand/BankstandPlugin.java
src/main/java/com/bankstand/http/HttpTransport.java
src/main/java/com/bankstand/http/OkHttpTransport.java
```
(Same four files as above — `BankstandClient`/`BankstandPlugin` reference the `OkHttpClient` type
only to pass it through to `OkHttpTransport`'s constructor, never to call it directly.)

What gets sent, and where, is disclosed per-toggle in the [README](../README.md#what-it-captures)
and in `runelite-plugin.properties`'s own `description`. The destination is whatever `serverBaseUrl`
the player configures (`https://bankstand.gg` by default); it is never hardcoded past that default
and never silently overridden.

## Does anything here drive the client

No. `NoAutomationApiTest` (`src/test/java/com/bankstand/NoAutomationApiTest.java`) source-scans
every file under `src/main/java` for `client.menuAction(`, `client.runScript(`, and
`client.invokeMenuAction(` and fails the build if any appear, specifically because Hub PR #11371
was rejected for exactly this. You can run it yourself:

```
$ ./gradlew test --tests NoAutomationApiTest
```

The plugin only reads client state (chat messages, widgets, varbits, the hiscores snapshot on
login) and, for the collection log specifically, prompts the player to manually open the log and
click Search themselves — the plugin reads what that produces, it never opens the log or clicks
anything on the player's behalf.

## Credentials

`NoCredentialsInConfigTest` (`src/test/java/com/bankstand/NoCredentialsInConfigTest.java`) enforces
that the pairing device token never passes through `ConfigManager`. It is written to a local file
under `RUNELITE_DIR` instead (`DeviceCredentialStore`), specifically so it is never included in a
synced RuneLite config profile. It is never logged, at any level, anywhere it is handled.

## No reflection, no native code, no automation beyond the game's own APIs

```
$ grep -rln "Class.forName\|Runtime.exec\|ProcessBuilder\|System.load(" src/main/java
```
(No output.) Production dependencies are `compileOnly net.runelite:client` only — no runtime
library beyond what RuneLite itself already provides.
