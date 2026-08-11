package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The prime-then-resolve state machine's pure logic (#660), tested without a live client. */
public class PetDropCaptureTest {

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
}
