package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AckedStateStoreTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private File file;
  private AckedStateStore store;

  @Before
  public void setUp() throws IOException {
    file = new File(folder.getRoot(), "acked.json");
    store = new AckedStateStore(file, new Gson());
  }

  private static Set<Integer> ids(Integer... values) {
    return new LinkedHashSet<>(Arrays.asList(values));
  }

  @Test
  public void aCharacterNothingIsKnownAboutReadsAsEmpty() {
    AckedState state = store.load(1234L);

    assertNull(state.getSkills());
    assertTrue(state.getCollectionLogItems().isEmpty());
    // Not zero. Zero would mean "the server acknowledged an empty log", which is a
    // different claim from never having been told.
    assertEquals(-1, state.getCollectionLogAcked());
  }

  @Test
  public void survivesARoundTrip() {
    AckedState state = AckedState.empty();
    state.setSkills("skills-digest");
    state.setQuests("quests-digest");
    state.setDiaries("diaries-digest");
    state.setCollectionLogItems(ids(1, 2, 3));
    state.setCollectionLogAcked(3);
    Map<String, Long> synced = new LinkedHashMap<>();
    synced.put("skills", 1_700_000_000_000L);
    state.setLastSyncedAt(synced);

    store.save(1234L, state);
    AckedState loaded = new AckedStateStore(file, new Gson()).load(1234L);

    assertEquals("skills-digest", loaded.getSkills());
    assertEquals("quests-digest", loaded.getQuests());
    assertEquals("diaries-digest", loaded.getDiaries());
    assertEquals(ids(1, 2, 3), loaded.getCollectionLogItems());
    assertEquals(3, loaded.getCollectionLogAcked());
    assertEquals(1_700_000_000_000L, (long) loaded.getLastSyncedAt().get("skills"));
  }

  /** A document written before this field existed has none in its JSON at all, not a
   *  null value: Gson leaves the field null on that class, and the store must still
   *  hand out an empty, iterable map rather than propagate the null. */
  @Test
  public void aDocumentMissingLastSyncedAtStillLoads() throws IOException {
    Files.write(
        file.toPath(),
        "{\"accounts\":{\"1234\":{\"skills\":\"only-skills\"}}}".getBytes(StandardCharsets.UTF_8));

    AckedState state = new AckedStateStore(file, new Gson()).load(1234L);

    assertTrue(state.getLastSyncedAt().isEmpty());
  }

  /** A baseline belongs to one character; two on one machine must not see each other's. */
  @Test
  public void keepsCharactersApart() {
    AckedState first = AckedState.empty();
    first.setSkills("first");
    first.setCollectionLogItems(ids(1, 2));
    AckedState second = AckedState.empty();
    second.setSkills("second");
    second.setCollectionLogItems(ids(9));

    store.save(1L, first);
    store.save(2L, second);

    assertEquals("first", store.load(1L).getSkills());
    assertEquals(ids(1, 2), store.load(1L).getCollectionLogItems());
    assertEquals("second", store.load(2L).getSkills());
    assertEquals(ids(9), store.load(2L).getCollectionLogItems());
  }

  @Test
  public void savingOneCharacterLeavesTheOtherAlone() {
    AckedState first = AckedState.empty();
    first.setSkills("first");
    store.save(1L, first);

    AckedState second = AckedState.empty();
    second.setSkills("second");
    store.save(2L, second);

    assertEquals("first", new AckedStateStore(file, new Gson()).load(1L).getSkills());
  }

  @Test
  public void aNegativeAccountHashIsARealAccount() {
    // The account hash is a signed 64-bit value and a negative one is real, which this
    // codebase has already been bitten by once.
    AckedState state = AckedState.empty();
    state.setSkills("negative");

    store.save(-8_234_567_890_123L, state);

    assertEquals("negative", new AckedStateStore(file, new Gson()).load(-8_234_567_890_123L).getSkills());
  }

  /**
   * The safe direction. A file we cannot read means we do not know what the server has,
   * and the only honest answer to that is to send everything again, which costs one
   * redundant submission. Refusing to start, or trusting a half-parsed document, both
   * cost more.
   */
  @Test
  public void treatsAnUnreadableFileAsEmptyRatherThanFailing() throws IOException {
    Files.write(file.toPath(), "{ this is not json".getBytes(StandardCharsets.UTF_8));

    AckedState state = new AckedStateStore(file, new Gson()).load(1234L);

    assertNull(state.getSkills());
    assertEquals(-1, state.getCollectionLogAcked());
  }

  @Test
  public void recoversByOverwritingACorruptFileOnTheNextSave() throws IOException {
    Files.write(file.toPath(), "not json at all".getBytes(StandardCharsets.UTF_8));
    AckedStateStore fresh = new AckedStateStore(file, new Gson());

    AckedState state = AckedState.empty();
    state.setSkills("recovered");
    fresh.save(1234L, state);

    assertEquals("recovered", new AckedStateStore(file, new Gson()).load(1234L).getSkills());
  }

  @Test
  public void aDocumentMissingItsCollectionLogFieldsStillLoads() throws IOException {
    Files.write(
        file.toPath(),
        "{\"accounts\":{\"1234\":{\"skills\":\"only-skills\"}}}".getBytes(StandardCharsets.UTF_8));

    AckedState state = new AckedStateStore(file, new Gson()).load(1234L);

    assertEquals("only-skills", state.getSkills());
    assertTrue(state.getCollectionLogItems().isEmpty());
  }

  @Test
  public void leavesNoTemporaryFileBehind() {
    store.save(1L, AckedState.empty());

    String[] names = folder.getRoot().list();
    assertEquals(1, names.length);
    assertEquals("acked.json", names[0]);
  }

  @Test
  public void createsTheParentDirectoryOnFirstSave() {
    File nested = new File(new File(folder.getRoot(), "bankstand"), "acked.json");
    AckedState state = AckedState.empty();
    state.setSkills("nested");

    new AckedStateStore(nested, new Gson()).save(7L, state);

    assertTrue(nested.exists());
    assertEquals("nested", new AckedStateStore(nested, new Gson()).load(7L).getSkills());
  }

  /** A caller mutating what it loaded must not reach through into the stored document. */
  @Test
  public void handsOutStateTheCallerCannotAliasIntoTheStore() {
    AckedState state = AckedState.empty();
    state.setCollectionLogItems(ids(1, 2));
    store.save(1L, state);

    AckedState loaded = store.load(1L);
    loaded.getCollectionLogItems().add(999);

    assertEquals(ids(1, 2), store.load(1L).getCollectionLogItems());
  }

  @Test
  public void aSavedDigestReplacesTheOneBefore() {
    AckedState first = AckedState.empty();
    first.setSkills("old");
    store.save(1L, first);

    AckedState second = AckedState.empty();
    second.setSkills("new");
    store.save(1L, second);

    assertEquals("new", store.load(1L).getSkills());
    assertNotEquals("old", store.load(1L).getSkills());
  }
}
