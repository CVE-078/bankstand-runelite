# Changelog

Every notable change to the Bankstand RuneLite plugin. Internal-only changes (refactors, test changes, dependency bumps) are not listed here.

## v0.4.0

- Your device token is only sent to the server you paired with. Point the Server URL somewhere else and nothing is sent until you pair with that server; switch back and syncing resumes.
- Redirects are never followed, so a token or capture is never re-sent to another address.
- A plain http Server URL is only kept for your own machine, judged by the address the request really goes to. Any other address is upgraded to https.
- Disconnect now also revokes the device on the server. If the server can't be reached, a chat line says so.
- Pairing again drops anything still waiting to be sent from the old pairing and revokes the old device, so nothing reaches another account or server. A chat line says how many events and loot drops were discarded.
- With Slayer tracker on, your slayer master, all three streaks and when you got the task are sent with your task.
- Only regular worlds are read: free, members, PvP, Bounty, high-risk and total-level worlds. Fresh Start, Last Man Standing worlds and any mode added later are now skipped too, and so is a Last Man Standing game on a normal world.
- Switching characters forgets the previous one's state at login, so nothing captured before the first sync can mix the two.
- The account type is remembered across restarts, so it is not sent again after every restart.
- If you paired with a self-hosted or test server before this version, pair again.
- A pairing that answers after you pressed Disconnect, or after you started a newer pairing, is ignored, so an old pairing can never come back.
- After pairing with another account or server, everything is sent again, so the new one is not missing what the old one already had.
- The Session log follows the same rules as every other capture: nothing inside a Last Man Standing game, and nothing until the welcome screen is closed after login.
- When Bankstand asks the plugin to slow down, events, loot and session marks wait for as long as it asks, and nothing is lost. Events for a character Bankstand cannot link yet are held for 30 minutes, or until you log in again or pair again.
- Bankstand now sees which plugin version you run.
- A pet that drops right after the game's pet insurance message is now recognised.
- 24 more collection log pets are recognised, including the God Wars Dungeon pets, Phoenix, Tumeken's guardian and Smol Heredit.
- Settings and README text now match what the plugin really does: Skill XP links your character and carries the regular update, other captures follow their own toggles, and pairing help points to Plugin connection on the website.
- With Diary progress on, the game's per-task diary values and reward claims are sent with your diaries, so Bankstand can show which tasks are done.

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
