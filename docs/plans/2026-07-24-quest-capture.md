# Plugin quest-state capture (#363 quests, Slice B) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Capture every quest's completion state behind a "Share quest progress" opt-in and include it as an optional `quests` block in the v1 submit envelope, mirroring the existing skill-XP capture.

**Architecture:** A new opt-in (`configManager` key, panel checkbox, default OFF) gates quest capture. When on, the existing `@Schedule` capture reads all `Quest.values()` states on the client thread (alongside the skill read, one consistent snapshot) into a `Map<String,String>` of `Quest.name()` -> `QuestState.name()`, gated by a new `QuestBaseline` (only submit on change), and appends a `quests` block to the envelope. Reuses `submitSnapshotWithRetry` and the generation-guarded advance unchanged. Server side (Slice A, #407) already accepts, gates, and per-user-merges the block.

**Tech Stack:** Java 11, Gradle, JUnit 4, RuneLite 1.12.33 (`net.runelite.api.Quest` = 210 constants, `Quest.getState(Client)` -> `net.runelite.api.QuestState` {NOT_STARTED, IN_PROGRESS, FINISHED}). Zero third-party runtime deps (Gson/OkHttp are RuneLite transitives). Design: bankstand `docs/superpowers/specs/2026-07-23-plugin-quest-ingest-design.md` sections 6 + 17. Structural template: `docs/plans/2026-07-23-skills-capture.md`.

## Global Constraints

- **Gate = `./gradlew build`** (compile + JUnit). No CI, so this is the merge gate. Single class: `./gradlew test --tests com.bankstand.<Class>`. Run it before each commit.
- **Wire contract is fixed by the server (Slice A, #407).** The `quests` block is `{ "<QUEST_NAME>": "NOT_STARTED"|"IN_PROGRESS"|"FINISHED" }`, keys are the `Quest` enum constant name (`Quest.name()`, SCREAMING_SNAKE), values are `QuestState.name()` (verbatim, no mapping table). The block is OPTIONAL and OMITTED when absent (mirrors `displayName`, NOT `skills`). `schemaVersion` stays `1`. The canonical fixture is bankstand `lib/plugin/contracts/submit-v1.quests.json`; the plugin fixture must match it (shape/keys/value-types; order-insensitive).
- **No allowlist needed for quests** (unlike the 23-skill allowlist): the server does not validate quest keys against a known set (only key length <= 64 and count <= 400; 210 quests is well under). Send all `Quest.values()`.
- **Opt-in, default OFF.** Skills stay always-on; quests are opt-in because quest data is more sensitive than hiscores (spec 17). The opt-in needs a plain-language disclosure in the panel.
- **Client thread only** for `Quest.getState(Client)` (reads varps/varbits) - read in the same `clientThread.invoke(...)` block as `readSkillXp()`.
- **Zero third-party runtime deps.** Do not add any library. No em dashes in code/comments. Conventional per-branch commit messages (`type(scope): subject`); the PR title (squash subject) is a plain imperative sentence.
- Client-side/self-mergeable after review. Going LIVE also needs the owner to merge #407 + set `PLUGIN_QUESTS_INGEST_ENABLED`; the build/tests here do not depend on that.

---

### Task 1: `QuestBaseline` (change-gate for quest states)

**Files:** Create `src/main/java/com/bankstand/QuestBaseline.java`; Create `src/test/java/com/bankstand/QuestBaselineTest.java`.

**Interfaces produced:** `QuestBaseline` with `boolean changedSince(Map<String,String> current)`, `void advance(Map<String,String> ackedNow)`, `void reset()`. Semantics identical to `SkillBaseline` but typed to `String` values.

- [ ] **Step 1: Read `src/main/java/com/bankstand/SkillBaseline.java`** to mirror its exact structure (a private `Map` field, the three methods, the class/comment style).

- [ ] **Step 2: Write the failing test** `QuestBaselineTest.java`, mirroring `SkillBaselineTest`: a fresh baseline reports `changedSince` true for any non-empty current; after `advance(m)`, `changedSince(m)` is false; a changed value (`"NOT_STARTED"` -> `"IN_PROGRESS"`) or a new key reports true; `reset()` makes everything changed again. Run `./gradlew test --tests com.bankstand.QuestBaselineTest` -> FAIL (class missing).

- [ ] **Step 3: Implement `QuestBaseline`** as a value-for-value copy of `SkillBaseline` with `Map<String,String>` instead of `Map<String,Integer>` (`.equals` on `String` works the same). Not thread-safe by design (touched only on the client-thread capture path), same as `SkillBaseline`.

- [ ] **Step 4:** `./gradlew test --tests com.bankstand.QuestBaselineTest` -> PASS. **Step 5: Commit** `feat: add QuestBaseline to gate quest-state submissions on change`.

---

### Task 2: `quests` block in the envelope + frozen contract fixture

**Files:** Modify `src/main/java/com/bankstand/SubmitEnvelope.java`; Create `src/test/resources/contracts/submit-v1.quests.json`; Modify `src/test/java/com/bankstand/SubmitEnvelopeContractTest.java` (and `SubmitEnvelopeTest.java` if it exercises `body`).

**Interfaces produced:** an OVERLOAD `SubmitEnvelope.body(submissionId, schemaVersion, pluginVersion, capturedAt, accountHash, displayName, skillXp, questStates)` where `questStates` is `Map<String,String>`; the existing 7-arg `body(...)` delegates to it with `questStates = null`. The `quests` key is appended after `skills` and OMITTED when `questStates` is null or empty.

- [ ] **Step 1: Read** `src/main/java/com/bankstand/SubmitEnvelope.java` and `src/test/java/com/bankstand/SubmitEnvelopeContractTest.java` for the exact current `body(...)` shape and the fixture-driven equality test pattern.

- [ ] **Step 2: Create the fixture** `src/test/resources/contracts/submit-v1.quests.json`, byte-identical to bankstand's `lib/plugin/contracts/submit-v1.quests.json`:

```json
{
  "submissionId": "018f9c8e-7b7a-7c00-8000-0000000abcde",
  "schemaVersion": 1,
  "pluginVersion": "1.0.0",
  "capturedAt": "2026-07-23T10:00:00.000Z",
  "accountHash": "123456789012345",
  "displayName": "Zezima",
  "skills": { "attack": { "xp": 5500000 } },
  "quests": { "COOKS_ASSISTANT": "FINISHED", "DRAGON_SLAYER_I": "IN_PROGRESS", "THE_RESTLESS_GHOST": "NOT_STARTED" }
}
```

- [ ] **Step 3: Write the failing contract test** in `SubmitEnvelopeContractTest.java`: a third `@Test` that reads `submit-v1.quests.json`, builds an envelope via the new 8-arg `body(...)` (skills `{attack:{xp:5500000}}`, quests `{COOKS_ASSISTANT:FINISHED, DRAGON_SLAYER_I:IN_PROGRESS, THE_RESTLESS_GHOST:NOT_STARTED}`), serializes with `new Gson().toJsonTree(body)`, and asserts order-insensitive `JsonObject` equality with the fixture. Run -> FAIL (no 8-arg overload / no `quests` key).

- [ ] **Step 4: Implement.** Add the 8-arg overload; append the `quests` block only when `questStates != null && !questStates.isEmpty()`:

```java
    if (questStates != null && !questStates.isEmpty()) {
      body.put("quests", new java.util.LinkedHashMap<>(questStates));
    }
```

Keep the existing 7-arg `body(...)` as a delegator (`return body(..., skillXp, null);`) so the two existing tests and the current `submitSnapshot` call site are unaffected. A plain `Map<String,String>` serializes to `{ "COOKS_ASSISTANT": "FINISHED", ... }`, matching the fixture.

- [ ] **Step 5:** `./gradlew test --tests com.bankstand.SubmitEnvelopeContractTest com.bankstand.SubmitEnvelopeTest` -> PASS (all three contract cases + any envelope unit tests). **Step 6: Commit** `feat: accept an optional quests block in the submit envelope`.

---

### Task 3: "Share quest progress" opt-in (config key + panel checkbox)

**Files:** Modify `src/main/java/com/bankstand/BankstandConfig.java`; Modify `src/main/java/com/bankstand/BankstandPanel.java` (and its `Listener`/callback wiring); tests as the panel's existing test coverage allows (`BankstandPluginTest` / a panel test if present).

**Interfaces produced:** `BankstandConfig.KEY_SHARE_QUESTS = "shareQuests"`; a helper to read it as a boolean (default false); a panel checkbox that writes it via `configManager.setConfiguration(GROUP, KEY_SHARE_QUESTS, ...)`.

- [ ] **Step 1: Read** `BankstandConfig.java` and `BankstandPanel.java` to match the constants-holder + panel-wiring pattern (there is NO RuneLite `@ConfigItem` interface in this repo; config is plain `configManager` keys read/written from the panel - keep it that way, Route A).

- [ ] **Step 2:** Add `public static final String KEY_SHARE_QUESTS = "shareQuests";` to `BankstandConfig`.

- [ ] **Step 3:** In `BankstandPanel`, add a `javax.swing.JCheckBox` labelled "Share quest progress" with a short disclosure line beneath it (e.g. "Sends which quests you have started and finished. Only you can see it. More personal than hiscore stats."). Wire its state change to `configManager.setConfiguration(BankstandConfig.GROUP, BankstandConfig.KEY_SHARE_QUESTS, checked)`, and initialise the checkbox from the stored value on build. Only show/enable it when paired (match how the panel gates its paired UI). Follow the panel's existing token/style conventions; no hardcoded surprises.

- [ ] **Step 4:** Add a small read helper (in the plugin or panel, wherever `isPaired()` lives) `boolean isQuestSharingEnabled()` -> `Boolean.parseBoolean(configManager.getConfiguration(GROUP, KEY_SHARE_QUESTS))` (null-safe, default false). This is the gate Task 4 reads.

- [ ] **Step 5:** `./gradlew build` -> PASS (panel compiles, existing tests green). **Step 6: Commit** `feat: add a Share quest progress opt-in to the panel`.

---

### Task 4: Capture quests and include them in the submission

**Files:** Modify `src/main/java/com/bankstand/BankstandPlugin.java`; Modify `src/test/java/com/bankstand/BankstandPluginTest.java`.

**Interfaces produced:** `readQuestStates()` -> `Map<String,String>` (all `Quest.values()`, `quest.name()` -> `quest.getState(client).name()`); the capture path submits when skills OR quests changed, includes the `quests` block only when the opt-in is on, and advances `questBaseline` on a stored (non-cooldown) accept under the generation guard.

- [ ] **Step 1: Read** `BankstandPlugin.java` (the `captureSkills()` `@Schedule`, the `clientThread.invoke` block, `readSkillXp()`, `onSkillsCaptured(...)`, `submitSnapshot(...)`, the `skillBaseline`/`baselineGeneration` fields, and the `session.isCurrent` advance guard) and `BankstandPluginTest.java` for its test harness (how it drives capture and asserts a submission body).

- [ ] **Step 2: Write failing plugin tests** in `BankstandPluginTest.java` covering:
  - With the opt-in ON, a capture whose quests changed produces a submission whose body has a `quests` map with `quest.name()` keys and `NOT_STARTED|IN_PROGRESS|FINISHED` values (mirror how the skills test asserts the body).
  - With the opt-in OFF, the submission body has NO `quests` key (skills-only), even if quest state would have changed.
  - With the opt-in ON but only quests changed (skills identical to baseline), a submission still fires (the quest change alone is enough).
  - `questBaseline` advances only on a stored accept, and resets on a generation change (mirror the skill-baseline tests).

  Match the existing test's mechanism for reading `Quest.getState` (the test likely stubs `Client`; stub `client.getSkillExperience` and the quest states via the mocked `Client`). Run -> FAIL.

- [ ] **Step 3: Implement `readQuestStates()`** (mirror `readSkillXp()`), iterating `Quest.values()`:

```java
  private Map<String, String> readQuestStates() {
    Map<String, String> quests = new LinkedHashMap<>();
    for (Quest quest : Quest.values()) {
      quests.put(quest.name(), quest.getState(client).name());
    }
    return quests;
  }
```

- [ ] **Step 4: Wire the capture.** In the `clientThread.invoke` block of `captureSkills()`, when `isQuestSharingEnabled()` also call `readQuestStates()` (same consistent snapshot), and pass both maps to the capture handler. Extend the handler (`onSkillsCaptured` -> rename/extend to `onCaptured` or add a quests param, whichever is the smaller diff): reset both baselines on a generation change; submit when `skillBaseline.changedSince(skills) || (questsIncluded && questBaseline.changedSince(quests))`; build the envelope via the 8-arg `body(...)` passing the quests map (or null when the opt-in is off); on a stored (non-cooldown) accept, advance `skillBaseline` and (when included) `questBaseline` under the same `session.isCurrent(accountHash, generation)` guard. Add a `questBaseline` field + reset it alongside the skill baseline. Keep the quests map OMITTED (null) from `body(...)` when the opt-in is off so no `quests` key is sent.

- [ ] **Step 5:** `./gradlew test --tests com.bankstand.BankstandPluginTest` -> PASS. **Step 6:** `./gradlew build` (full gate). **Step 7: Commit** `feat: capture quest state and include it in the submission when opted in`.

---

## After all tasks

- [ ] **Adversarial review:** one `correctness-reviewer` on the whole branch diff (the opt-in gate: quests never sent when off; the OMIT-when-absent envelope semantics; the combined skills-or-quests submit gate and dual baseline advance under the generation guard; quest keys = `Quest.name()` matching the server fixture; no allowlist over-send). A `security-reviewer` is not needed: no auth/token/endpoint change (reuses `submitSnapshotWithRetry`), the data surface is the client's own opt-in capture.
- [ ] **Update the ledger** each task in `.superpowers/sdd/progress.md`.
- [ ] **Self-merge** (client-side) after the review is clean and `./gradlew build` is green. PR title = plain imperative sentence ("Add quest-state capture behind a Share quest progress opt-in"); single-commit-vs-multi handled per bankstand CLAUDE.md's squash-subject rule if relevant (this repo squash-merges too - match its `git log` style).
- [ ] **Note in the PR** that end-to-end needs the owner to merge #407 + set `PLUGIN_QUESTS_INGEST_ENABLED`, and a `./gradlew run` dev-session to see it live (not covered by JUnit).

## Follow-ups
- Slice C (bankstand): the claimant guides quest-list overlay + the RuneLite-`Quest.name()` -> guide-slug map.
- A dev-run verification pass once the server flag is on.
