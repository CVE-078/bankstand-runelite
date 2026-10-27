package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

/** The plugin needs a live client to run, so where it reads the token is pinned on the source. */
public class TokenOriginWiringTest {

  private static String plugin() throws IOException {
    return new String(
        Files.readAllBytes(
            Paths.get("src", "main", "java", "com", "bankstand", "BankstandPlugin.java")),
        StandardCharsets.UTF_8);
  }

  @Test
  public void everySendTakesTheTokenForTheConfiguredServer() throws IOException {
    String source = plugin();
    int raw = source.split("getToken\\(\\)", -1).length - 1;
    // Disconnect and a new pairing revoke the old token, at the server it was paired with.
    assertEquals("only the two revokes may read the raw token", 2, raw);
    assertTrue(source.contains("paired.pairedServerUrl(), paired.getToken()"));
    assertTrue(source.contains("previous.pairedServerUrl(), previous.getToken()"));
    assertTrue(source.contains("return deviceStore.load().tokenFor(savedServerUrl()) != null;"));
  }

  @Test
  public void aNewPairingRecordsTheServerItWasMadeWith() throws IOException {
    String source = plugin();
    assertTrue(source.contains("storeToken(res, url, ticket)"));
    assertTrue(source.contains("deviceStore.save(DeviceCredentials.of(res, url))"));
  }

  private static String body(String source, String signature) {
    int start = source.indexOf(signature);
    assertTrue(signature, start >= 0);
    return source.substring(start, source.indexOf("\n  }\n", start));
  }

  @Test
  public void everyQueuedSendReadsItsTokenAndQueueAsOneStepWhenItRuns() throws IOException {
    String source = plugin();
    String[][] sends = {
      {"public void drainEventOutbox() {", "eventOutbox.pending()"},
      {"private void runPresenceSends() {", "presenceQueue.peek()"},
      {"private void sendLoot() {", "lootOutbox.pending()"},
    };
    for (String[] send : sends) {
      String body = body(source, send[0]);
      int lock = body.indexOf("synchronized (pairingQueues) {");
      int end = body.indexOf("}", lock);
      int token = body.indexOf("deviceStore.load().tokenFor(url)", lock);
      int queue = body.indexOf(send[1], lock);
      assertTrue(send[0] + " reads the token under the pairing lock", lock >= 0 && token < end);
      assertTrue(send[0] + " reads the queue under the pairing lock", queue > lock && queue < end);
    }
    assertTrue(body(source, "private void sendPresence() {").contains("this::runPresenceSends"));
  }

  @Test
  public void aReplacedTokensAuthFailureNeverHaltsTheNewPairing() throws IOException {
    String source = plugin();
    String snapshot = body(source, "private void submitSnapshot(");
    int skip = snapshot.indexOf("if (isReplaced(url, token)) {");
    assertTrue(skip >= 0 && skip < snapshot.indexOf("haltIfCurrent(url, token)"));
    String identity = body(source, "private void submitIdentity(");
    int skipIdentity = identity.indexOf("if (isReplaced(url, token)) {");
    assertTrue(skipIdentity >= 0 && skipIdentity < identity.indexOf("identityFailureNotice("));
  }

  @Test
  public void aCooldownRetryIsDroppedWhenThePairingChanged() throws IOException {
    String snapshot = body(plugin(), "private void submitSnapshot(");
    int retry = snapshot.indexOf("executor.schedule(");
    int check = snapshot.indexOf("|| isReplaced(url, token)) {", retry);
    assertTrue(retry >= 0 && check > retry && check < snapshot.indexOf("submitSnapshot(", retry));
  }
}
