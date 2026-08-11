# Bankstand RuneLite plugin

The RuneLite companion for [Bankstand](https://bankstand.gg). It links your client to your Bankstand
account and keeps your progress on the site up to date without you having to look anything up.

You generate a short pairing code on the website and paste it into the plugin's settings. The plugin
exchanges it for a device token stored locally, and from then on it submits quietly in the background.
Your linked character shows a "RuneLite verified" badge on Bankstand.

**Everything captured is private to your own Bankstand account.** None of it is public, none of it is
ranked, and nothing here decides who can see it. That is a separate setting on the website.

## What it captures

Each of these is a separate toggle, and each names exactly what it sends.

- **Skill XP.** On by default. Checked every 60 seconds while you are logged in, and sent only when
  something actually changed, so an idle account sends nothing.
- **Quest progress** and **achievement diary progress.** Opt-in. Diaries are captured at tier level:
  a tier reads complete or not.
- **Combat achievements.** Opt-in. Sends a completed count per tier, because that is all the client
  exposes for the tier as a whole, plus which task as the game announces its completion in chat.
  A tier reading 23 of 41 cannot say which 23 from before you turned this on; only tasks completed
  while the plugin is running are named.
- **Collection log.** Opt-in. Sends which slots you have filled, and each new unlock as the game
  announces it in chat, so you do not have to open the log to have it captured. The log is not held
  in the client otherwise, so browsing adds what you see, and **opening it and clicking Search**
  reads the whole thing in one go, with an infobox counting items as they arrive. A partial read
  adds to what is known and never replaces it.
- **Account type.** Opt-in. Sends whether this account is a main, an ironman, or one of the group
  types. The hiscores cannot show a Group Ironman at all, so without this Bankstand has to take your
  word for it. Your own answer still wins: Bankstand shows you both and asks.
- **Notable drops.** Opt-in. Sends unique, untradeable or high-value drops as they happen: the item,
  its value where it has one, and where it came from. A tradeable drop is sent when its total GE
  value clears a threshold you set (1,000,000 gp by default). Untradeable items are judged by name
  instead, not that number.
- **Pet drops.** Opt-in. Sends which pet you received and when, as it happens.

It also takes one last capture as you log out, so the final minute of a session is not lost to the
60 second schedule. A cleared client reads as zeroes, and that read is rejected rather than sent.

### What it does not capture

Stated plainly, because a gap is easy to mistake for a bug.

- **Individual diary tasks.** Not exposed per task, so a diary tier at 21 of 22 reports nothing.
- **Bank value, worn equipment, inventory and your location.** Not captured, not offered, and not
  requestable by the server.
- **Your own conversations.** Never sent. The game's own broadcast lines are read where a capability
  above says so (a collection log unlock, a combat achievement completion), never anything you or
  anyone else typed.

## Setting it up

1. Sign in at [bankstand.gg](https://bankstand.gg) and go to **Account > Connect RuneLite**. Generate
   a pairing code.
2. In RuneLite, open **Settings > Bankstand** and paste the code into **Pairing code**. The field
   clears itself and the chat box confirms the connection.
3. Log in as a character you have tracked on Bankstand. Shortly after login the chat box shows
   **Verified as `<name>`**, and the badge appears on that character.

If the character is not one you have tracked, the plugin says so rather than binding silently.

## Configuration

Everything lives under **Settings > Bankstand**, in two sections.

The split matters. **Connection** is which Bankstand account this client is tied to. **Collect** is
what this client reads and sends. Who may then *see* any of it is a third question, answered in your
Bankstand privacy settings and deliberately not here: a setting in a game client cannot be the source
of truth for a server-side audience, which is why nothing in this plugin is called "share".

### Connection

- **Server URL.** Defaults to the live site. Leave it alone unless you are running Bankstand
  yourself. A stale address here makes every update fail.
- **Pairing code.** Paste a code to pair. Cleared automatically once used, successfully or not.
- **Disconnect.** Tick to forget this device's token. Unticks itself. To revoke it server-side, use
  **Bankstand > Account**.

Your pairing is stored in `<RUNELITE_DIR>/bankstand/device.json`, not in RuneLite's plugin
configuration, so it is never uploaded by config sync. **Each machine pairs separately**, and each
appears as its own device on Bankstand with its own name and its own revoke button.

### Collect

One toggle per capability, as listed above. **Collect skill XP** gates the whole capture rather than
just its own block: the submission format makes skills required and the rest optional riders on it,
so with it off there is nothing for the others to attach to and a paired client goes quiet.

If you paired before version 0.1.0, these toggles were renamed and reverted to off. Re-tick the ones
you want. Your pairing and server URL are untouched.

## In-game commands

Type these in the chat box. `::stand` works as a shorthand for all of them.

- `::bstand` shows the connection status: which account, which character, when it last sent, and what
  it last sent.
- `::bstand sync` sends now rather than waiting for the next cycle.
- `::bstand link` re-links this character.
- `::bstand log` arms a guided collection log read without needing to find the right-click entry on an
  already-open log. Needs collection log capture on and the log interface open, and says so if either
  is not true.
- `::bstand repair` clears the stored pairing so a stale or revoked token has an obvious fix: paste a
  fresh code in the plugin settings afterwards.

## When something goes wrong

Outcomes are reported in the chat box, since the plugin has no panel to hold a status line. A failure
that repeats is announced once rather than every cycle, and the recovery is announced when it clears.

A client that keeps failing also slows down, backing off to about 16 minutes and resuming the moment
one attempt succeeds. **A rejected or revoked token stops it entirely**: retrying cannot fix that, so
it says so once and sends nothing until you pair again.

## Building

Requires JDK 11 or later.

```
./gradlew build
```

That compiles the plugin and runs the tests, and it is the same command CI runs on every pull
request.

## Reporting a problem

[Open an issue](https://github.com/CVE-078/bankstand-runelite/issues/new/choose). Include the
plugin version and whatever the chat box said, which is usually enough to answer it.

**Never paste your pairing code or device token.** The code is single use and the token is a
credential. The plugin logs neither, so an ordinary log is safe to share.

## Contributing

The development rules, the invariants behind the design and the module layout are maintained in the
Bankstand project repository rather than here.

Two constraints are worth stating up front, because breaking either fails review rather than a test:

- **The plugin observes the client. It never drives it.** `client.menuAction`, `client.runScript` and
  `client.invokeMenuAction` are banned, and a test fails the build if one reappears.
- **Never log the device token, the account hash, the display name, or a raw request body.** The
  first is a credential; the rest identify a real account.

## Licence

BSD 2-Clause. See [`LICENSE`](LICENSE). No runtime dependencies beyond what `runelite-client` already
ships.

Bankstand is an independent project and is not affiliated with, endorsed by or sponsored by Jagex Ltd
or the RuneLite project. Old School RuneScape is a trademark of Jagex Ltd.
