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
    // The one exception is disconnect, which revokes at the server the token was paired with.
    assertEquals("only disconnect may read the raw token", 1, raw);
    assertTrue(source.contains("paired.pairedServerUrl(), paired.getToken()"));
    assertTrue(source.contains("return deviceStore.load().tokenFor(savedServerUrl()) != null;"));
  }

  @Test
  public void aNewPairingRecordsTheServerItWasMadeWith() throws IOException {
    String source = plugin();
    assertTrue(source.contains("storeToken(res, url);"));
    assertTrue(source.contains("deviceStore.save(DeviceCredentials.of(res, url));"));
  }
}
