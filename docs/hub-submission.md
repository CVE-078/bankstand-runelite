# Plugin Hub submission

Everything the submission needs, written down so it is reviewed once rather than composed
in a hurry against a PR template. Tracked as bankstand#644.

## Where the disclosure actually lives

The `warning=` field in the **hub manifest**, a file added to `runelite/plugin-hub` under
`plugins/bankstand`. Not `runelite-plugin.properties` in this repo, which the README does
not mention. WikiSync and the merged `osrs-bank-sync` both use it, and the latter is the
model for specificity: it names the exact fields sent and warns that a changed URL sends
data elsewhere.

**This is the only disclosure a player is guaranteed to read.** The config panel renders
`description` as a hover tooltip, so a player who never hovers sees four toggle labels and
nothing else. Do not write the warning assuming the panel covers half of it.

## Manifest

```
repository=https://github.com/CVE-078/bankstand-runelite.git
commit=<full sha of the submitted commit>
```

The `warning=` text depends on bankstand#669, which is unresolved. Both versions below are
accurate for their case; pick the one matching the decision.

### If the device token stays in RuneLite config (option 1 on #669)

> This plugin sends your account hash, display name and skill XP to Bankstand while you
> are paired, plus any of quest progress, achievement diary progress and collection log
> contents that you switch on. Bankstand keeps this data private to your account by
> default; you can choose to share supported data with your Bankstand groups or publicly
> from your Bankstand privacy settings. Your pairing token is stored in RuneLite's plugin
> configuration, so if you have RuneLite config sync enabled it is also stored by
> RuneLite. Data is sent to whatever Server URL you configure; the default is the official
> Bankstand server over https. If you change it, your data goes to that server instead.

### If the token moves out of config (options 2 or 3 on #669)

> This plugin sends your account hash, display name and skill XP to Bankstand while you
> are paired, plus any of quest progress, achievement diary progress and collection log
> contents that you switch on. Bankstand keeps this data private to your account by
> default; you can choose to share supported data with your Bankstand groups or publicly
> from your Bankstand privacy settings. Data is sent to whatever Server URL you configure;
> the default is the official Bankstand server over https. If you change it, your data
> goes to that server instead.

Combat achievements are deliberately absent from both: nothing captures them, so naming
them would disclose something that does not happen.

## Pre-submission checklist

- [x] `version` is a real version, not `1.0-SNAPSHOT`
- [x] `description` states that data leaves the client
- [x] `build=standard`, no HTTP dependency added (a `gradle` build forces manual hash
      verification and slows review)
- [x] No `client.menuAction`, `client.runScript` or `client.invokeMenuAction`, enforced by
      `NoAutomationApiTest`
- [x] No reflection, no native code
- [x] CI green on the submitted commit
- [ ] `warning=` chosen, per bankstand#669
- [ ] Submitted from the account that owns the repository

## Observed rejection causes, and where we stand

None of these is external transmission. A plugin sending entire bank contents to a
configurable URL was merged (#12470).

| Cause | Example | Us |
| --- | --- | --- |
| Duplicates an existing hub plugin | #13342 | No hub plugin syncs to Bankstand |
| Banned API use | #11371, `client.menuAction` | Removed, and guarded by a test |
| Misusing another service's API | #12026 | We talk only to our own server |
| Submitted from the wrong account | #13100 | Submit from CVE-078 |
