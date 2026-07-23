# Plugin skill-XP capture (bankstand-runelite) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** The RuneLite plugin periodically captures the 23 skills' XP and POSTs the frozen v1 envelope to `POST /api/plugin/v1/submit`, reusing the existing device-token auth + bounded retry, gated by a change-since-last-ack baseline.

**Architecture:** Pure, testable units (a dependency-free UUIDv7 generator, an envelope builder, a change-gate baseline, `submitSnapshot` behind the `HttpTransport` seam) plus thin `BankstandPlugin` glue that reads skills on the client thread, diffs the baseline, and dispatches the submit off-thread. This is the client counterpart to the server ingest (#361); the server write is gated off, so this exercises the endpoint + the frozen contract and gives the plugin its capture path ready for when the flag flips on.

**Tech Stack:** Java 11, RuneLite client API (compileOnly), Gson (RuneLite transitive), JUnit 4. No new runtime dependencies (Plugin Hub requirement). Server contract frozen in bankstand `lib/plugin/contracts/submit-v1.*.json`.

## Global Constraints

- **Zero third-party RUNTIME deps.** Only `net.runelite:client` (compileOnly) + its transitives (Gson, OkHttp). JUnit is test-scope. Do not add a UUID or JSON library.
- **Gate = local `./gradlew build`** (this repo has NO CI). Run it before every commit; it compiles + runs JUnit.
- **No em dashes** anywhere in code or comments. Use a comma, period, or parentheses.
- **Comments** document the timeless why; no issue numbers, dates, or spec paths.
- **Commits:** conventional `type(scope): subject`, subject-only, no body, no `Co-Authored-By` / AI-attribution trailer.
- **Never log** the device token, account hash, display name, or the raw body.
- **Frozen contract:** the mirrored fixtures under `src/test/resources/contracts/` must be byte-identical to bankstand's `lib/plugin/contracts/submit-v1.valid.json` and `submit-v1.minimal.json`. The v1 envelope shape is: `{ submissionId (UUIDv7 string), schemaVersion (number, 1), pluginVersion (string), capturedAt (ISO-8601 string), accountHash (decimal STRING), displayName? (string), skills ({ [lowercaseSkillName]: { xp: number } }) }`.
- **The 23 skill names** (lowercase, no "overall"): attack, defence, strength, hitpoints, ranged, prayer, magic, cooking, woodcutting, fletching, fishing, firemaking, crafting, smithing, mining, herblore, agility, thieving, slayer, farming, runecraft, hunter, construction.
- **Tests use the existing `FakeTransport`/`SequencedTransport` pattern** in `BankstandClientTest.java` (inline JSON string literals, `HttpTransport` fake, no network). Match that style.

---

### Task 1: Dependency-free UUIDv7 generator

**Files:**
- Create: `src/main/java/com/bankstand/UuidV7.java`
- Test: `src/test/java/com/bankstand/UuidV7Test.java`

**Interfaces:**
- Produces: `public final class UuidV7 { public static String generate(); }` returning a canonical 36-char lowercase UUID string whose version nibble is `7` and variant bits are `10`.

- [ ] **Step 1: Write the failing test**

```java
package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.UUID;
import org.junit.Test;

public class UuidV7Test {
  @Test
  public void generatesACanonicalVersion7Uuid() {
    String s = UuidV7.generate();
    // Canonical 8-4-4-4-12 form, parseable by java.util.UUID.
    UUID parsed = UUID.fromString(s);
    assertEquals("36 chars", 36, s.length());
    assertEquals("version 7", 7, parsed.version());
    assertEquals("RFC variant", 2, parsed.variant());
    assertEquals("lowercase", s, s.toLowerCase());
  }

  @Test
  public void generatesDistinctValues() {
    assertNotEquals(UuidV7.generate(), UuidV7.generate());
  }

  @Test
  public void isTimeOrderedAcrossAShortDelay() throws Exception {
    String a = UuidV7.generate();
    Thread.sleep(2);
    String b = UuidV7.generate();
    // UUIDv7 leads with a millisecond timestamp, so lexical order tracks time.
    assertTrue("later uuid sorts after earlier", b.compareTo(a) > 0);
  }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests com.bankstand.UuidV7Test`
Expected: FAIL to compile (`UuidV7` missing).

- [ ] **Step 3: Implement**

```java
package com.bankstand;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * A dependency-free UUIDv7 generator. UUIDv7 leads with a 48-bit Unix
 * millisecond timestamp, so ids are time-ordered, which keeps them useful as an
 * idempotency key without needing a separate sequence number. The random bits do
 * not need to be unpredictable (this is an idempotency and ordering key, not a
 * secret), but a decent RNG keeps collisions away within a millisecond.
 */
public final class UuidV7 {
  private UuidV7() {}

  private static final SecureRandom RANDOM = new SecureRandom();

  public static String generate() {
    long ts = System.currentTimeMillis();
    byte[] b = new byte[16];
    RANDOM.nextBytes(b);
    // 48-bit big-endian millisecond timestamp in bytes 0..5.
    b[0] = (byte) (ts >>> 40);
    b[1] = (byte) (ts >>> 32);
    b[2] = (byte) (ts >>> 24);
    b[3] = (byte) (ts >>> 16);
    b[4] = (byte) (ts >>> 8);
    b[5] = (byte) ts;
    // Version 7 in the high nibble of byte 6.
    b[6] = (byte) ((b[6] & 0x0f) | 0x70);
    // RFC 4122 variant (10xx) in the high bits of byte 8.
    b[8] = (byte) ((b[8] & 0x3f) | 0x80);
    long msb = 0;
    long lsb = 0;
    for (int i = 0; i < 8; i++) {
      msb = (msb << 8) | (b[i] & 0xff);
    }
    for (int i = 8; i < 16; i++) {
      lsb = (lsb << 8) | (b[i] & 0xff);
    }
    return new UUID(msb, lsb).toString();
  }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test --tests com.bankstand.UuidV7Test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bankstand/UuidV7.java src/test/java/com/bankstand/UuidV7Test.java
git commit -m "feat: add a dependency-free UUIDv7 generator"
```

---

### Task 2: Envelope builder + snapshot response DTO

**Files:**
- Create: `src/main/java/com/bankstand/SubmitEnvelope.java`
- Create: `src/main/java/com/bankstand/dto/SubmitSnapshotResponse.java`
- Test: `src/test/java/com/bankstand/SubmitEnvelopeTest.java`

**Interfaces:**
- Produces:
  - `SubmitEnvelope.body(String submissionId, int schemaVersion, String pluginVersion, String capturedAt, long accountHash, String displayName, Map<String,Integer> skillXp): Map<String,Object>` — an ordered map matching the v1 wire shape. `accountHash` is emitted as a decimal STRING; `displayName` is omitted when null/blank; `skills` is `{ name: { "xp": Integer } }`.
  - `SubmitEnvelope.SCHEMA_VERSION = 1`.
  - `dto.SubmitSnapshotResponse` with Gson-populated getters: `boolean isAccepted()`, `boolean isStored()`, `String getReason()`, `int getEventsCreated()`, `String getServerTime()`, `String getNextSubmitAfter()`.

- [ ] **Step 1: Write the failing test**

```java
package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class SubmitEnvelopeTest {
  private static Map<String, Integer> skills() {
    Map<String, Integer> s = new LinkedHashMap<>();
    s.put("attack", 5500000);
    s.put("slayer", 101333);
    return s;
  }

  @Test
  public void buildsTheV1WireShape() {
    Map<String, Object> body =
        SubmitEnvelope.body(
            "018f9c8e-7b7a-7c00-8000-000000000abc",
            1,
            "1.0.0",
            "2026-07-23T10:00:00.000Z",
            123456789012345L,
            "Zezima",
            skills());
    JsonObject json = new Gson().toJsonTree(body).getAsJsonObject();

    assertEquals("018f9c8e-7b7a-7c00-8000-000000000abc", json.get("submissionId").getAsString());
    assertEquals(1, json.get("schemaVersion").getAsInt());
    assertEquals("1.0.0", json.get("pluginVersion").getAsString());
    assertEquals("2026-07-23T10:00:00.000Z", json.get("capturedAt").getAsString());
    // account hash is a decimal STRING, not a number.
    assertTrue(json.get("accountHash").getAsJsonPrimitive().isString());
    assertEquals("123456789012345", json.get("accountHash").getAsString());
    assertEquals("Zezima", json.get("displayName").getAsString());
    assertEquals(5500000, json.getAsJsonObject("skills").getAsJsonObject("attack").get("xp").getAsInt());
  }

  @Test
  public void omitsDisplayNameWhenBlank() {
    Map<String, Object> body =
        SubmitEnvelope.body("id", 1, "1.0.0", "t", 1L, null, skills());
    assertFalse(new Gson().toJsonTree(body).getAsJsonObject().has("displayName"));
  }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests com.bankstand.SubmitEnvelopeTest`
Expected: FAIL to compile.

- [ ] **Step 3: Implement**

`src/main/java/com/bankstand/SubmitEnvelope.java`:

```java
package com.bankstand;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the v1 skills-submit envelope as an ordered map, ready for Gson. The
 * account hash is emitted as a decimal string because a 64-bit value does not fit
 * a JSON number safely, matching the server's contract. The shape is frozen; see
 * the mirrored contract fixtures under src/test/resources/contracts.
 */
public final class SubmitEnvelope {
  private SubmitEnvelope() {}

  public static final int SCHEMA_VERSION = 1;

  public static Map<String, Object> body(
      String submissionId,
      int schemaVersion,
      String pluginVersion,
      String capturedAt,
      long accountHash,
      String displayName,
      Map<String, Integer> skillXp) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("submissionId", submissionId);
    body.put("schemaVersion", schemaVersion);
    body.put("pluginVersion", pluginVersion);
    body.put("capturedAt", capturedAt);
    body.put("accountHash", Long.toString(accountHash));
    if (displayName != null && !displayName.trim().isEmpty()) {
      body.put("displayName", displayName);
    }
    Map<String, Object> skills = new LinkedHashMap<>();
    for (Map.Entry<String, Integer> e : skillXp.entrySet()) {
      Map<String, Object> stat = new LinkedHashMap<>();
      stat.put("xp", e.getValue());
      skills.put(e.getKey(), stat);
    }
    body.put("skills", skills);
    return body;
  }
}
```

`src/main/java/com/bankstand/dto/SubmitSnapshotResponse.java`:

```java
package com.bankstand.dto;

/**
 * The response from a v1 skills submit: whether the server accepted it, whether it
 * stored the update, a machine reason (persisted|duplicate|cooldown|stale|
 * regression|unclaimed|not_applied), and pacing hints. Populated by Gson.
 */
public class SubmitSnapshotResponse {
  private boolean accepted;
  private boolean stored;
  private String reason;
  private int eventsCreated;
  private String serverTime;
  private String nextSubmitAfter;

  public boolean isAccepted() {
    return accepted;
  }

  public boolean isStored() {
    return stored;
  }

  public String getReason() {
    return reason;
  }

  public int getEventsCreated() {
    return eventsCreated;
  }

  public String getServerTime() {
    return serverTime;
  }

  public String getNextSubmitAfter() {
    return nextSubmitAfter;
  }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test --tests com.bankstand.SubmitEnvelopeTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bankstand/SubmitEnvelope.java src/main/java/com/bankstand/dto/SubmitSnapshotResponse.java src/test/java/com/bankstand/SubmitEnvelopeTest.java
git commit -m "feat: add the v1 skills envelope builder and response DTO"
```

---

### Task 3: Frozen contract fixtures + JUnit contract test

**Files:**
- Create: `src/test/resources/contracts/submit-v1.valid.json`
- Create: `src/test/resources/contracts/submit-v1.minimal.json`
- Test: `src/test/java/com/bankstand/SubmitEnvelopeContractTest.java`

**Interfaces:**
- Consumes: `SubmitEnvelope.body(...)` (Task 2).
- Produces: the cross-repo contract lock. The Java-built envelope, fed the fixture's own field values, must serialize to a JSON object equal to the frozen fixture. Byte-identical mirror of bankstand's fixtures.

- [ ] **Step 1: Mirror the frozen fixtures (byte-identical to bankstand)**

`src/test/resources/contracts/submit-v1.valid.json`:

```json
{
  "submissionId": "018f9c8e-7b7a-7c00-8000-000000000abc",
  "schemaVersion": 1,
  "pluginVersion": "1.0.0",
  "capturedAt": "2026-07-23T10:00:00.000Z",
  "accountHash": "123456789012345",
  "displayName": "Zezima",
  "skills": {
    "attack": { "xp": 5500000 },
    "slayer": { "xp": 101333 }
  }
}
```

`src/test/resources/contracts/submit-v1.minimal.json`:

```json
{
  "submissionId": "018f9c8e-7b7a-7c00-8000-000000000def",
  "schemaVersion": 1,
  "pluginVersion": "1.0.0",
  "capturedAt": "2026-07-23T10:00:00.000Z",
  "accountHash": "123456789012345",
  "skills": {}
}
```

- [ ] **Step 2: Write the failing test**

```java
package com.bankstand;

import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class SubmitEnvelopeContractTest {
  private static JsonObject fixture(String name) throws Exception {
    try (Reader r =
        new InputStreamReader(
            SubmitEnvelopeContractTest.class.getResourceAsStream("/contracts/" + name),
            StandardCharsets.UTF_8)) {
      return JsonParser.parseReader(r).getAsJsonObject();
    }
  }

  @Test
  public void javaEnvelopeMatchesTheFrozenValidFixture() throws Exception {
    JsonObject want = fixture("submit-v1.valid.json");
    Map<String, Integer> skills = new LinkedHashMap<>();
    skills.put("attack", 5500000);
    skills.put("slayer", 101333);
    // Build the envelope from the fixture's own field values.
    Map<String, Object> body =
        SubmitEnvelope.body(
            want.get("submissionId").getAsString(),
            want.get("schemaVersion").getAsInt(),
            want.get("pluginVersion").getAsString(),
            want.get("capturedAt").getAsString(),
            Long.parseLong(want.get("accountHash").getAsString()),
            want.get("displayName").getAsString(),
            skills);
    JsonObject got = new Gson().toJsonTree(body).getAsJsonObject();
    // JsonObject equality is order-insensitive, so this pins the shape, keys, and
    // value types against the frozen server contract.
    assertEquals(want, got);
  }

  @Test
  public void javaEnvelopeMatchesTheFrozenMinimalFixture() throws Exception {
    JsonObject want = fixture("submit-v1.minimal.json");
    Map<String, Object> body =
        SubmitEnvelope.body(
            want.get("submissionId").getAsString(),
            want.get("schemaVersion").getAsInt(),
            want.get("pluginVersion").getAsString(),
            want.get("capturedAt").getAsString(),
            Long.parseLong(want.get("accountHash").getAsString()),
            null,
            new LinkedHashMap<>());
    JsonObject got = new Gson().toJsonTree(body).getAsJsonObject();
    assertEquals(want, got);
  }
}
```

- [ ] **Step 3: Run to verify it passes**

Run: `./gradlew test --tests com.bankstand.SubmitEnvelopeContractTest`
Expected: PASS. If it fails, the builder (Task 2) diverges from the frozen contract; fix the builder, not the fixture (the fixture is the source of truth shared with the server).

- [ ] **Step 4: Commit**

```bash
git add src/test/resources/contracts/ src/test/java/com/bankstand/SubmitEnvelopeContractTest.java
git commit -m "test: pin the v1 submit envelope to the frozen server contract"
```

---

### Task 4: `BankstandClient.submitSnapshot` + shared bounded retry

**Files:**
- Modify: `src/main/java/com/bankstand/BankstandClient.java`
- Test: `src/test/java/com/bankstand/BankstandClientTest.java`

**Interfaces:**
- Consumes: `SubmitEnvelope` shape (Task 2), `SubmitSnapshotResponse` (Task 2), the existing `SubmitException` (with its `retryable` flag), `HttpTransport`.
- Produces:
  - `SubmitSnapshotResponse submitSnapshot(String baseUrl, String deviceToken, Map<String,Object> envelopeBody) throws SubmitException`.
  - `SubmitSnapshotResponse submitSnapshotWithRetry(String baseUrl, String deviceToken, Map<String,Object> envelopeBody, int maxAttempts, long baseDelayMillis) throws SubmitException`.
  - A private generic retry helper reused by BOTH `submitIdentityWithRetry` and `submitSnapshotWithRetry` (DRY the existing loop).

- [ ] **Step 1: Write the failing tests**

Add to `BankstandClientTest.java`:

```java
@Test
public void submitsASnapshotWithBearerAndEnvelope() throws Exception {
  FakeTransport t =
      new FakeTransport(
          new HttpResponse(
              200,
              "{\"accepted\":true,\"stored\":false,\"reason\":\"not_applied\","
                  + "\"eventsCreated\":0,\"serverTime\":\"2026-07-23T10:00:00.000Z\","
                  + "\"nextSubmitAfter\":\"2026-07-23T10:01:00.000Z\"}"));
  java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
  body.put("submissionId", "018f9c8e-7b7a-7c00-8000-000000000abc");
  body.put("schemaVersion", 1);

  SubmitSnapshotResponse res =
      new BankstandClient(t, new Gson())
          .submitSnapshot(BASE + "/", "bsd_tok", body);

  assertTrue(res.isAccepted());
  assertFalse(res.isStored());
  assertEquals("not_applied", res.getReason());
  assertEquals(BASE + "/api/plugin/v1/submit", t.url);
  assertEquals("Bearer bsd_tok", t.headers.get("Authorization"));
  assertTrue("envelope serialized in body", t.body.contains("\"submissionId\""));
}

@Test
public void submitSnapshotWithoutTokenFailsBeforeAnyRequest() {
  FakeTransport t = new FakeTransport(new HttpResponse(200, "{}"));
  try {
    new BankstandClient(t, new Gson())
        .submitSnapshot(BASE, "", new java.util.LinkedHashMap<>());
    fail("expected SubmitException");
  } catch (SubmitException e) {
    assertFalseCalled(t);
  }
}

@Test
public void submitSnapshotRetriesARetryableFailureThenSucceeds() throws Exception {
  SequencedTransport t =
      new SequencedTransport(
          new IOException("blip"),
          new HttpResponse(200, "{\"accepted\":true,\"stored\":true,\"reason\":\"persisted\"}"));
  SubmitSnapshotResponse res =
      new BankstandClient(t, new Gson())
          .submitSnapshotWithRetry(BASE, "bsd_tok", new java.util.LinkedHashMap<>(), 3, 0L);
  assertTrue(res.isStored());
  assertEquals(2, t.calls);
}

@Test
public void submitSnapshotMapsA401ToATerminalFailure() {
  FakeTransport t = new FakeTransport(new HttpResponse(401, "{\"error\":\"unauthorized\"}"));
  try {
    new BankstandClient(t, new Gson())
        .submitSnapshotWithRetry(BASE, "bsd_tok", new java.util.LinkedHashMap<>(), 3, 0L);
    fail("expected SubmitException");
  } catch (SubmitException e) {
    assertFalse(e.isRetryable());
  }
}
```

(Add `import static org.junit.Assert.assertFalse;` if not present.)

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests com.bankstand.BankstandClientTest`
Expected: FAIL to compile (`submitSnapshot` missing).

- [ ] **Step 3: Implement `submitSnapshot` + extract the shared retry**

In `BankstandClient.java`: add `submitSnapshot`, `submitSnapshotWithRetry`, and a generic retry helper; route the existing `submitIdentityWithRetry` through the same helper (behaviour unchanged, existing retry tests still cover it).

```java
// Add a functional seam for the retry helper (near the top-level of the class):
private interface SubmitCall<T> {
  T call() throws SubmitException;
}

// Generic bounded retry: retryable failures are retried up to maxAttempts with a
// linear backoff of baseDelayMillis * attempt; terminal failures fail fast. Blocks
// the calling thread during backoff, so it runs on the plugin's background executor.
private static <T> T withRetry(SubmitCall<T> call, int maxAttempts, long baseDelayMillis)
    throws SubmitException {
  SubmitException last = null;
  for (int attempt = 1; attempt <= maxAttempts; attempt++) {
    try {
      return call.call();
    } catch (SubmitException e) {
      last = e;
      if (!e.isRetryable() || attempt == maxAttempts) {
        throw e;
      }
      sleep(baseDelayMillis * attempt);
    }
  }
  throw last; // unreachable for maxAttempts >= 1
}

// Replace the body of submitIdentityWithRetry with a delegation:
public SubmitResponse submitIdentityWithRetry(
    String baseUrl, String deviceToken, long accountHash, String displayName,
    int maxAttempts, long baseDelayMillis) throws SubmitException {
  return withRetry(
      () -> submitIdentity(baseUrl, deviceToken, accountHash, displayName),
      maxAttempts, baseDelayMillis);
}

/**
 * Submits a v1 skills envelope (already built by SubmitEnvelope), authenticated by
 * the device token. Status handling mirrors submitIdentity: 401/403 terminal, 429/5xx
 * retryable, other non-200 terminal, IOException retryable. The token is never logged.
 */
public SubmitSnapshotResponse submitSnapshot(
    String baseUrl, String deviceToken, Map<String, Object> envelopeBody) throws SubmitException {
  if (isBlank(deviceToken)) {
    throw new SubmitException("Not paired.");
  }
  String url = trimTrailingSlash(baseUrl) + SUBMIT_PATH;
  Map<String, String> headers = new LinkedHashMap<>();
  headers.put("Content-Type", "application/json");
  headers.put("Accept", "application/json");
  headers.put("User-Agent", USER_AGENT);
  headers.put("Authorization", "Bearer " + deviceToken);

  HttpResponse response;
  try {
    response = transport.post(url, gson.toJson(envelopeBody), headers);
  } catch (IOException e) {
    throw new SubmitException("Could not reach Bankstand.", true);
  }
  int status = response.getStatus();
  if (status != 200) {
    if (status == 401 || status == 403) {
      throw new SubmitException(
          "Bankstand rejected the device token. Re-pair in Account > Connect RuneLite.", false);
    }
    if (status == 429 || status >= 500) {
      throw new SubmitException("Bankstand is busy.", true);
    }
    throw new SubmitException("Bankstand rejected the update.", false);
  }
  try {
    SubmitSnapshotResponse parsed = gson.fromJson(response.getBody(), SubmitSnapshotResponse.class);
    if (parsed == null) {
      throw new SubmitException("Unexpected response from Bankstand.");
    }
    return parsed;
  } catch (JsonSyntaxException e) {
    throw new SubmitException("Unexpected response from Bankstand.");
  }
}

public SubmitSnapshotResponse submitSnapshotWithRetry(
    String baseUrl, String deviceToken, Map<String, Object> envelopeBody,
    int maxAttempts, long baseDelayMillis) throws SubmitException {
  return withRetry(
      () -> submitSnapshot(baseUrl, deviceToken, envelopeBody), maxAttempts, baseDelayMillis);
}
```

Add the import: `import com.bankstand.dto.SubmitSnapshotResponse;`.

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test --tests com.bankstand.BankstandClientTest`
Expected: PASS, including the pre-existing identity retry tests (unchanged behaviour through the shared helper).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bankstand/BankstandClient.java src/test/java/com/bankstand/BankstandClientTest.java
git commit -m "feat: add submitSnapshot with a shared bounded retry"
```

---

### Task 5: Skill change-gate baseline

**Files:**
- Create: `src/main/java/com/bankstand/SkillBaseline.java`
- Test: `src/test/java/com/bankstand/SkillBaselineTest.java`

**Interfaces:**
- Produces: `class SkillBaseline` with `boolean changedSince(Map<String,Integer> current)`, `void advance(Map<String,Integer> acked)`, `void reset()`. Not thread-safe; the plugin uses it only from the client-thread capture path.

- [ ] **Step 1: Write the failing test**

```java
package com.bankstand;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class SkillBaselineTest {
  private static Map<String, Integer> of(String k, int v) {
    Map<String, Integer> m = new LinkedHashMap<>();
    m.put(k, v);
    return m;
  }

  @Test
  public void everythingIsChangedAgainstAFreshBaseline() {
    assertTrue(new SkillBaseline().changedSince(of("attack", 100)));
  }

  @Test
  public void unchangedAfterAdvance() {
    SkillBaseline b = new SkillBaseline();
    b.advance(of("attack", 100));
    assertFalse(b.changedSince(of("attack", 100)));
  }

  @Test
  public void anXpIncreaseIsAChange() {
    SkillBaseline b = new SkillBaseline();
    b.advance(of("attack", 100));
    assertTrue(b.changedSince(of("attack", 200)));
  }

  @Test
  public void aNewSkillKeyIsAChange() {
    SkillBaseline b = new SkillBaseline();
    b.advance(of("attack", 100));
    Map<String, Integer> more = of("attack", 100);
    more.put("slayer", 5);
    assertTrue(b.changedSince(more));
  }

  @Test
  public void resetForgetsTheBaseline() {
    SkillBaseline b = new SkillBaseline();
    b.advance(of("attack", 100));
    b.reset();
    assertTrue(b.changedSince(of("attack", 100)));
  }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests com.bankstand.SkillBaselineTest`
Expected: FAIL to compile.

- [ ] **Step 3: Implement**

```java
package com.bankstand;

import java.util.HashMap;
import java.util.Map;

/**
 * The last skill-XP vector the server acknowledged, used as a change gate so the
 * capture loop only submits when something moved. The plugin resets this on an
 * account switch and advances it only when a submit is acknowledged, so a dropped
 * submit is retried on the next capture (the baseline never moved).
 */
public class SkillBaseline {
  private final Map<String, Integer> acked = new HashMap<>();

  /** True when any skill in {@code current} is new or differs from the baseline. */
  public boolean changedSince(Map<String, Integer> current) {
    for (Map.Entry<String, Integer> e : current.entrySet()) {
      Integer prev = acked.get(e.getKey());
      if (prev == null || !prev.equals(e.getValue())) {
        return true;
      }
    }
    return false;
  }

  public void advance(Map<String, Integer> ackedNow) {
    acked.clear();
    acked.putAll(ackedNow);
  }

  public void reset() {
    acked.clear();
  }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test --tests com.bankstand.SkillBaselineTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bankstand/SkillBaseline.java src/test/java/com/bankstand/SkillBaselineTest.java
git commit -m "feat: add the skill-XP change-gate baseline"
```

---

### Task 6: Plugin capture cadence + panel status (wiring)

**Files:**
- Modify: `src/main/java/com/bankstand/BankstandPlugin.java`
- Modify: `src/main/java/com/bankstand/BankstandPanel.java`

**Interfaces:**
- Consumes: `UuidV7` (T1), `SubmitEnvelope` (T2), `SubmitSnapshotResponse` (T2), `BankstandClient.submitSnapshotWithRetry` (T4), `SkillBaseline` (T5), `AccountSession`.

This task is plugin glue that composes the tested units; it is verified by `./gradlew build` (compile) plus a dev-run eyeball, not by JUnit (the plugin class has no unit-test harness, matching the reliability slice). Keep all real logic in the tested units above.

- [ ] **Step 1: Inject `ClientThread` and add capture constants**

Add imports and fields to `BankstandPlugin`:

```java
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.task.Schedule;
```

```java
  @Inject private ClientThread clientThread;

  // Capture the 23 skills on a fixed cadence and submit only when they changed since
  // the last acknowledged submit. The interval matches the server's per-device
  // cooldown so a change is reported at most once per window.
  private static final int CAPTURE_INTERVAL_SECONDS = 60;

  private final SkillBaseline skillBaseline = new SkillBaseline();
  // The generation the baseline currently tracks; a change means the account switched
  // and the baseline must be forgotten so the new account submits afresh.
  private int baselineGeneration = -1;
```

- [ ] **Step 2: Add the scheduled capture**

The `@Schedule` method runs off the client thread; it reads game state on the client thread via `clientThread.invoke`, then hands the captured vector to a submit dispatch. Add to `BankstandPlugin`:

```java
  @Schedule(period = CAPTURE_INTERVAL_SECONDS, unit = ChronoUnit.SECONDS)
  public void captureSkills() {
    if (pairingClient == null || !isPaired() || !session.isActive()) {
      return;
    }
    if (client.getGameState() != GameState.LOGGED_IN) {
      return;
    }
    // Skip non-standard worlds (tournament, seasonal, leagues, deadman, PvP arena):
    // their XP is not the account's main-game progression.
    EnumSet<WorldType> worldType = client.getWorldType();
    if (!isStandardWorld(worldType)) {
      return;
    }
    // Read the skills and the identity on the client thread, into one consistent
    // snapshot, then dispatch the submit off-thread.
    clientThread.invoke(
        () -> {
          Player local = client.getLocalPlayer();
          if (local == null) {
            return;
          }
          String name = local.getName();
          long accountHash = session.getAccountHash();
          int generation = session.getGeneration();
          Map<String, Integer> skills = readSkillXp();
          onSkillsCaptured(accountHash, generation, name, skills);
        });
  }

  // Only the main game (no non-standard world-type flags) carries real progression.
  private static boolean isStandardWorld(EnumSet<WorldType> worldType) {
    return !worldType.contains(WorldType.TOURNAMENT_WORLD)
        && !worldType.contains(WorldType.SEASONAL)
        && !worldType.contains(WorldType.DEADMAN)
        && !worldType.contains(WorldType.PVP_ARENA);
  }

  // Reads current XP for the 23 tracked skills, keyed by lowercase name. Runs on the
  // client thread (getSkillExperience must not be called off it).
  private Map<String, Integer> readSkillXp() {
    Map<String, Integer> skills = new LinkedHashMap<>();
    for (Skill skill : Skill.values()) {
      String key = skill.getName().toLowerCase();
      skills.put(key, client.getSkillExperience(skill));
    }
    return skills;
  }
```

Note on `Skill.values()`: recent RuneLite `Skill` enums contain exactly the trackable skills (no `OVERALL`). If the running client's enum includes a name outside the canonical 23, the server rejects the unknown key; keep this method sending only what `Skill.values()` yields and rely on the frozen server allowlist. If a build-time compile error shows `OVERALL` present, skip it explicitly.

- [ ] **Step 3: Add the change-gate + submit dispatch**

Runs on the client thread (from `captureSkills`); does the cheap baseline diff, then dispatches the network submit on the executor.

```java
  private void onSkillsCaptured(
      long accountHash, int generation, String name, Map<String, Integer> skills) {
    // A change of account forgets the baseline so the new account submits afresh.
    if (generation != baselineGeneration) {
      skillBaseline.reset();
      baselineGeneration = generation;
    }
    if (!skillBaseline.changedSince(skills)) {
      return;
    }
    submitSnapshot(accountHash, generation, name, skills);
  }

  private void submitSnapshot(
      long accountHash, int generation, String name, Map<String, Integer> skills) {
    String url = savedServerUrl();
    String token =
        configManager.getConfiguration(BankstandConfig.GROUP, BankstandConfig.KEY_DEVICE_TOKEN);
    String version = getClass().getPackage().getImplementationVersion();
    String pluginVersion = version != null ? version : "dev";
    Map<String, Object> body =
        SubmitEnvelope.body(
            UuidV7.generate(),
            SubmitEnvelope.SCHEMA_VERSION,
            pluginVersion,
            Instant.now().toString(),
            accountHash,
            name,
            skills);
    executor.submit(
        () -> {
          try {
            SubmitSnapshotResponse res =
                pairingClient.submitSnapshotWithRetry(
                    url, token, body, MAX_SUBMIT_ATTEMPTS, SUBMIT_RETRY_BASE_DELAY_MS);
            // Advance the baseline when the server accepted and was not rate-limiting
            // us; a cooldown means try the same change again next cycle. This makes a
            // dropped or throttled submit self-heal without a client-side queue.
            if (res.isAccepted() && !"cooldown".equals(res.getReason())) {
              // Advance on the client thread: the baseline is only touched there.
              clientThread.invoke(() -> skillBaseline.advance(skills));
            }
            if (panel != null && session.isCurrent(accountHash, generation)) {
              panel.showSnapshotOutcome(res.isStored(), res.getReason());
            }
          } catch (SubmitException e) {
            // Do not advance the baseline: the change is unsent, retry next cycle. The
            // token and account hash are never logged.
            if (panel != null && session.isCurrent(accountHash, generation)) {
              panel.showSubmitFailed(e.getMessage());
            }
          }
        });
  }
```

Note: `submitSnapshotWithRetry` requires `import com.bankstand.dto.SubmitSnapshotResponse;` in the plugin.

- [ ] **Step 4: Add the panel status method**

In `BankstandPanel.java`, add a method mirroring `showSubmitFailed`'s style (EDT-marshalled, kept in the connected state):

```java
  /** Reports a skills-sync outcome while staying in the connected state. */
  void showSnapshotOutcome(boolean stored, String reason) {
    SwingUtilities.invokeLater(
        () -> {
          if (stored) {
            statusLabel.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
            statusLabel.setText("<html>Stats synced.</html>");
          } else {
            // Accepted but not stored (e.g. ingest not enabled yet, or a stale/duplicate
            // submit). Not an error; keep it muted.
            statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            statusLabel.setText("<html>Stats received.</html>");
          }
        });
  }
```

- [ ] **Step 5: Build (full gate)**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. The unchecked-operations note in the dev-run entry point is pre-existing, not from this change.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/bankstand/BankstandPlugin.java src/main/java/com/bankstand/BankstandPanel.java
git commit -m "feat: capture skill xp on a cadence and submit the v1 envelope"
```

---

## After all tasks

- [ ] **Adversarial review.** One `correctness-reviewer` pass on the diff (client logic: the retry refactor keeping identity behaviour, the baseline gate, the capture/advance-on-ack flow, the world guard). No `security-reviewer` needed: this is client-side, adds no server auth/RLS/API surface (it calls the existing gated endpoint). Scale per CLAUDE.md.
- [ ] **Self-merge** is authorized (client-side plugin logic, no auth/RLS/migration on the server) after the review is clean and `./gradlew build` is green: squash-merge the branch, matching the reliability slice.
- [ ] **Dev-run note (not blocking):** the capture cadence and panel copy were not exercised in a live `./gradlew run` (needs an interactive RuneLite session on a real account). Worth an eyeball on the next dogfood run, especially the client-thread read and the once-per-change cadence.
- [ ] **Update roadmap #307** in the same session: record Plan B shipped, and note the end-to-end value stays gated until the server write flag is enabled (#397 trust design).

## Follow-ups (file, do not build here)

- A logout/hop flush (capture the final gains before a break); periodic-only is the first-slice choice.
- Respect the server's `nextSubmitAfter` client-side to avoid predictable cooldown no-ops (today the interval equals the cooldown, so at most one wasted call per change).
- A perfect-square cycle-skip backoff during a sustained server outage (on top of the per-call retry).
