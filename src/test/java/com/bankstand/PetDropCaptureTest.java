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

/** The prime-then-resolve state machine's pure logic (#660), tested without a live client. */
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

  /**
   * The toggle gates deriving-and-emitting, not the one-shot prime/consume
   * tracking (see {@code handleMessage}'s own doc for why). Feeds the exact
   * prime-then-resolve sequence for a known pet while disabled and confirms
   * nothing reaches the outbox either way.
   */
  @Test
  public void handleMessageEmitsNothingWhenDisabled() throws IOException {
    File file = new File(folder.newFolder("bankstand"), "events.json");
    EventOutbox outbox = new EventOutbox(file, new Gson());
    PetDropCapture capture = new PetDropCapture(outbox, () -> false, () -> 1L);

    capture.handleMessage("You have a funny feeling like you're being followed.");
    capture.handleMessage("New item added to your collection log: Baby mole");

    assertTrue(outbox.pending().isEmpty());
  }

  /**
   * The one-shot consumption must survive a toggle-off between the prime and its
   * resolve. If it did not, disabling between the two would leave {@code primed}
   * stuck true with nothing to clear it, and a later, unrelated message naming a
   * known pet after re-enabling would wrongly resolve that stale prime, exactly
   * the failure the one-shot design exists to prevent.
   */
  @Test
  public void aPrimeConsumedWhileDisabledIsNotWronglyResolvedAfterReEnabling() throws IOException {
    File file = new File(folder.newFolder("bankstand"), "events.json");
    EventOutbox outbox = new EventOutbox(file, new Gson());
    boolean[] enabled = {true};
    PetDropCapture capture = new PetDropCapture(outbox, () -> enabled[0], () -> 1L);

    capture.handleMessage("You have a funny feeling like you're being followed.");
    enabled[0] = false;
    // The resolve message arrives while disabled: nothing is emitted, but the
    // one-shot prime must still be consumed here, not left stuck for later.
    capture.handleMessage("Untradeable drop: Heron");
    enabled[0] = true;
    // An unrelated later message that happens to name a known pet must NOT be
    // treated as resolving anything: there is no active prime by this point.
    capture.handleMessage("Untradeable drop: Baby mole");

    assertTrue(outbox.pending().isEmpty());
  }
}
