package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ManifestStoreTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private ManifestStore store(String name) {
    return new ManifestStore(new File(folder.getRoot(), name), new Gson());
  }

  private static CapabilityManifest.RawManifest raw(String json) {
    return new Gson().fromJson(json, CapabilityManifest.RawManifest.class);
  }

  private static final String GOOD =
      "{\"schemaVersion\":1,\"minPluginVersion\":\"0.1.0\","
          + "\"capabilities\":[\"skills\",\"quests\"],\"uploadIntervalSeconds\":300}";

  @Test
  public void roundTripsAManifest() {
    ManifestStore s = store("m.json");
    s.save(raw(GOOD));
    assertEquals(Arrays.asList("skills", "quests"), s.load().capabilities());
  }

  @Test
  public void hasNothingBeforeAnythingIsSaved() {
    assertNull(store("absent.json").load());
  }

  @Test
  public void revalidatesOnTheWayOut() throws Exception {
    // The file is on disk where anything can edit it. A cached manifest naming a
    // capability this build no longer supports must not be honoured just because it was
    // once written by us.
    File f = new File(folder.getRoot(), "tampered.json");
    Files.write(
        f.toPath(),
        ("{\"schemaVersion\":1,\"capabilities\":[\"skills\",\"bank\"],"
                + "\"uploadIntervalSeconds\":1}")
            .getBytes(StandardCharsets.UTF_8));
    CapabilityManifest loaded = new ManifestStore(f, new Gson()).load();
    assertEquals(Arrays.asList("skills"), loaded.capabilities());
    // And the interval is clamped on the way out too, not merely on the way in.
    assertEquals(
        CapabilityManifest.MIN_UPLOAD_INTERVAL_SECONDS, loaded.uploadIntervalSeconds());
  }

  @Test
  public void treatsAnUnreadableFileAsNothingCached() throws Exception {
    File f = new File(folder.getRoot(), "junk.json");
    Files.write(f.toPath(), "not json at all".getBytes(StandardCharsets.UTF_8));
    assertNull(new ManifestStore(f, new Gson()).load());
  }

  @Test
  public void prefersAFreshManifestOverTheCache() {
    ManifestStore s = store("order.json");
    s.save(raw(GOOD));
    CapabilityManifest fresh =
        CapabilityManifest.validate(
            raw("{\"schemaVersion\":1,\"capabilities\":[\"diaries\"],\"uploadIntervalSeconds\":300}"));
    assertEquals(Arrays.asList("diaries"), s.current(fresh).capabilities());
  }

  @Test
  public void fallsBackToTheCacheThenToTheBundle() {
    ManifestStore s = store("fallback.json");
    // Nothing fetched, nothing cached: the compiled-in manifest, so a client that has
    // never reached the server still captures what it was built to capture.
    assertTrue(s.current(null).allows("skills"));
    assertEquals(
        CapabilityManifest.SUPPORTED_CAPABILITIES.size(), s.current(null).capabilities().size());

    // Nothing fetched but something cached: the cache wins over the bundle.
    s.save(raw(GOOD));
    assertEquals(Arrays.asList("skills", "quests"), s.current(null).capabilities());
  }

  @Test
  public void survivesSavingNothing() {
    ManifestStore s = store("null.json");
    s.save(null);
    assertNull(s.load());
  }
}
