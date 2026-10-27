package com.bankstand;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

/** Disconnect needs a live client to run, so its order is pinned on the source. */
public class DisconnectTest {

  @Test
  public void clearsLocallyFirstThenRevokesOffTheClientThread() throws IOException {
    String source =
        new String(
            Files.readAllBytes(
                Paths.get("src", "main", "java", "com", "bankstand", "BankstandPlugin.java")),
            StandardCharsets.UTF_8);
    int start = source.indexOf("private void disconnect() {");
    String body = source.substring(start, source.indexOf("\n  }\n", start));

    int readToken = body.indexOf("DeviceCredentials paired = deviceStore.load();");
    int clear = body.indexOf("pairingQueues.replace(() -> removed[0] = deviceStore.clear());");
    int unpair = body.indexOf("api.unpairInBackground(");
    assertTrue("reads the token before forgetting it", readToken >= 0 && readToken < clear);
    assertTrue(
        "revokes at the server it was paired with, not the one now configured",
        body.indexOf("paired.pairedServerUrl(), paired.getToken()", unpair) > unpair);
    assertTrue("clears before and regardless of the server call", clear < unpair);
    assertTrue(
        "tells the player when the server could not be reached",
        body.indexOf("notice(UNPAIR_UNREACHABLE)", unpair) > unpair);
  }
}
