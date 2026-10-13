# Changelog

Every notable change to the Bankstand RuneLite plugin. Internal-only changes (refactors, test changes, dependency bumps) are not listed here.

## v0.3.0

- Play sessions: when you log in and out, with XP, kills and loot per session. Off by default, under Play tracking.
- Full loot log per monster, not just notable drops. Off by default.
- Your current slayer task, points and streak. Off by default.
- Settings are grouped into Connection, Progress, Drops and Play tracking, with the server URL under Advanced.
- A change the server turns away for being too soon after the last one is retried once, so a quest completion or your last sync before logout isn't lost.
- The "re-pair" notice shows once instead of on every tick.
- `::bstand sync` no longer lists the collection log as syncing.

## v0.2.1 (not released separately; folded into v0.3.0)

- Notable drops no longer include PvP loot, so another player's name is never sent.
- Nothing is captured on Leagues, Deadman, tournament, PvP Arena, beta, no-save or quest speedrunning worlds. This now covers the logout capture and every chat and loot event, not just the regular capture.
- The logout capture is skipped if nothing was captured earlier in the session, since a cleared client cannot be told apart then.
- Waits until the welcome screen is closed and a few ticks have passed after login before reading anything, so values still loading are not sent.
- Combat achievement tier completions are recognised even when the game adds text around the message.
- The server URL must use https. Plain http works only for localhost, and any other http address is upgraded to https.
- The diary progress setting now describes everything it sends.
- An event the server can't accept is no longer retried every minute.
- A quest too new for RuneLite's quest list is still sent when you complete it, using the name on the quest-complete scroll.

## v0.2.0

- Added a sidebar status panel: when each capability last synced, your recent activity, and a manual sync button.
- Added `::bstand export` (share your current capture settings) and `::bstand help` (list every command).
- Combat achievements now also capture completed task counts per boss or activity, not just per tier.
- Combat achievements now also capture the moment a whole tier finishes, not just individual tasks.
- Fixed a bug where a captured combat achievement task name could carry a stray icon character.
