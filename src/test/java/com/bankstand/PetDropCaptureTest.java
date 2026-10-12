package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PetDropCaptureTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  @Test
  public void recognisesBothPrimeMessageForms() {
    assertTrue(PetDropCapture.isPrimeMessage("You have a funny feeling like you're being followed."));
    assertTrue(PetDropCapture.isPrimeMessage("You feel something weird sneaking into your backpack."));
  }

  @Test
  public void anUnrelatedMessageDoesNotPrime() {
    assertFalse(PetDropCapture.isPrimeMessage("You have received a drop: Coins."));
  }

  @Test
  public void resolvesAKnownPetFromTheCollectionLogLine() {
    String name = PetDropCapture.resolvePetName("New item added to your collection log: Baby mole");
    assertEquals("Baby mole", name);
  }

  @Test
  public void resolvesAKnownPetFromTheUntradeableDropLine() {
    String name = PetDropCapture.resolvePetName("Untradeable drop: Heron");
    assertEquals("Heron", name);
  }

  @Test
  public void aNonPetUntradeableIsIgnored() {
    assertNull(PetDropCapture.resolvePetName("Untradeable drop: Clue scroll (elite)"));
  }

  /** Broadcasts use the pet's real display casing, so matching must be case-insensitive. */
  @Test
  public void resolvesAKnownPetRegardlessOfCasing() {
    assertEquals(
        "TzRek-Jad",
        PetDropCapture.resolvePetName("Untradeable drop: TzRek-Jad"));
    assertEquals(
        "Baby Mole",
        PetDropCapture.resolvePetName("New item added to your collection log: Baby Mole"));
    assertEquals(
        "Giant Squirrel",
        PetDropCapture.resolvePetName("Untradeable drop: Giant Squirrel"));
    assertEquals(
        "Pet Dagannoth Prime",
        PetDropCapture.resolvePetName("Untradeable drop: Pet Dagannoth Prime"));
  }

  @Test
  public void anUnrecognisedMessageResolvesToNull() {
    assertNull(PetDropCapture.resolvePetName("Congratulations, you've completed a hard Combat Task."));
  }

  @Test
  public void payloadCarriesTheNameAndNoSource() {
    java.util.Map<String, Object> payload = PetDropCapture.payload("Baby mole");
    assertEquals("Baby mole", payload.get("petName"));
    assertNull(payload.get("source"));
  }

  /** The toggle gates emitting, not the one-shot prime tracking. */
  @Test
  public void handleMessageEmitsNothingWhenDisabled() throws IOException {
    File file = new File(folder.newFolder("bankstand"), "events.json");
    EventOutbox outbox = new EventOutbox(file, new Gson());
    PetDropCapture capture = new PetDropCapture(outbox, () -> false, () -> 1L);

    capture.handleMessage("You have a funny feeling like you're being followed.");
    capture.handleMessage("New item added to your collection log: Baby mole");

    assertTrue(outbox.pending().isEmpty());
  }

  /** A toggle-off between prime and resolve must still consume the prime, or it stays stuck. */
  @Test
  public void aPrimeConsumedWhileDisabledIsNotWronglyResolvedAfterReEnabling() throws IOException {
    File file = new File(folder.newFolder("bankstand"), "events.json");
    EventOutbox outbox = new EventOutbox(file, new Gson());
    boolean[] enabled = {true};
    PetDropCapture capture = new PetDropCapture(outbox, () -> enabled[0], () -> 1L);

    capture.handleMessage("You have a funny feeling like you're being followed.");
    enabled[0] = false;
    capture.handleMessage("Untradeable drop: Heron");
    enabled[0] = true;
    // No active prime remains, so this must not resolve anything.
    capture.handleMessage("Untradeable drop: Baby mole");

    assertTrue(outbox.pending().isEmpty());
  }
}
