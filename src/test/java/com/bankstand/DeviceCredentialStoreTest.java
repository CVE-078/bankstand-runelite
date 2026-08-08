package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * The pairing credentials, kept in a file rather than in {@code ConfigManager}.
 *
 * <p>Every failure here resolves to "not paired", which asks the player to pair again.
 * The alternative, carrying on with a credential we could not read, submits against a
 * token that may not be ours.
 */
public class DeviceCredentialStoreTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private DeviceCredentialStore storeIn(File file) {
    return new DeviceCredentialStore(file, new Gson());
  }

  private File newFile() throws IOException {
    return new File(folder.newFolder("bankstand"), "device.json");
  }

  @Test
  public void roundTripsAPairing() throws IOException {
    File file = newFile();
    DeviceCredentials saved = new DeviceCredentials();
    saved.setToken("tok_abc");
    saved.setDeviceId("dev_1");
    saved.setExpiresAt("2027-01-01T00:00:00Z");
    storeIn(file).save(saved);

    DeviceCredentials loaded = storeIn(file).load();

    assertEquals("tok_abc", loaded.getToken());
    assertEquals("dev_1", loaded.getDeviceId());
    assertEquals("2027-01-01T00:00:00Z", loaded.getExpiresAt());
    assertTrue(loaded.isPaired());
  }

  @Test
  public void readsAsUnpairedWhenNothingWasEverWritten() throws IOException {
    DeviceCredentials loaded = storeIn(newFile()).load();

    assertFalse(loaded.isPaired());
    assertNull(loaded.getToken());
  }

  @Test
  public void readsAsUnpairedWhenTheFileIsCorrupt() throws IOException {
    // A truncated write, a half-synced file, a hand-edit. Never a parse failure the
    // player has to diagnose, and never a partially-populated credential.
    File file = newFile();
    Files.write(file.toPath(), "{ not json".getBytes(StandardCharsets.UTF_8));

    assertFalse(storeIn(file).load().isPaired());
  }

  @Test
  public void treatsABlankTokenAsUnpaired() throws IOException {
    File file = newFile();
    DeviceCredentials blank = new DeviceCredentials();
    blank.setToken("   ");
    storeIn(file).save(blank);

    assertFalse(storeIn(file).load().isPaired());
  }

  @Test
  public void clearForgetsThePairing() throws IOException {
    File file = newFile();
    DeviceCredentials saved = new DeviceCredentials();
    saved.setToken("tok_abc");
    storeIn(file).save(saved);

    storeIn(file).clear();

    assertFalse(storeIn(file).load().isPaired());
  }

  @Test
  public void clearOnAnAlreadyEmptyStoreIsNotAnError() throws IOException {
    storeIn(newFile()).clear();
  }

  @Test
  public void createsItsDirectoryOnFirstSave() throws IOException {
    // The plugin points this at <RUNELITE_DIR>/bankstand, which does not exist on a
    // fresh install until something writes to it.
    File file = new File(new File(folder.getRoot(), "not-created-yet"), "device.json");
    DeviceCredentials saved = new DeviceCredentials();
    saved.setToken("tok_abc");

    storeIn(file).save(saved);

    assertTrue(storeIn(file).load().isPaired());
  }

  @Test
  public void aFailedSaveDoesNotDestroyThePreviousPairing() throws IOException {
    // Temp-then-move, so a crash between opening the file and finishing the write
    // cannot leave a truncated token where a working one used to be.
    File file = newFile();
    DeviceCredentials first = new DeviceCredentials();
    first.setToken("tok_first");
    storeIn(file).save(first);

    DeviceCredentials second = new DeviceCredentials();
    second.setToken("tok_second");
    storeIn(file).save(second);

    assertEquals("tok_second", storeIn(file).load().getToken());
  }
}
