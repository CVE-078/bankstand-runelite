package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class CapabilityManifestTest {

  private static CapabilityManifest.RawManifest parse(String json) {
    return new Gson().fromJson(json, CapabilityManifest.RawManifest.class);
  }

  @Test
  public void cannotExpressAnythingButPrimitives() {
    // **The whole guarantee, asserted rather than described.** A Plugin Hub reviewer's
    // question is whether a compromised server could make this plugin read a bank, an
    // inventory, chat or a location. It cannot, because the wire type has no field that
    // could carry a varbit, a script, a widget, a URL, a class name or an expression.
    // This fails the moment somebody adds one.
    for (Field field : CapabilityManifest.RawManifest.class.getDeclaredFields()) {
      if (field.isSynthetic()) {
        continue;
      }
      Type type = field.getGenericType();
      boolean ok;
      if (type == int.class || type == long.class || type == boolean.class || type == String.class) {
        ok = true;
      } else if (type instanceof ParameterizedType) {
        ParameterizedType p = (ParameterizedType) type;
        // The one container allowed, and only of strings.
        ok = p.getRawType() == List.class && p.getActualTypeArguments()[0] == String.class;
      } else {
        ok = false;
      }
      assertTrue(
          "RawManifest." + field.getName() + " is not a primitive the contract allows: " + type,
          ok);
    }
  }

  @Test
  public void acceptsAWellFormedManifest() {
    CapabilityManifest m =
        CapabilityManifest.validate(
            parse(
                "{\"schemaVersion\":1,\"minPluginVersion\":\"0.1.0\","
                    + "\"capabilities\":[\"skills\",\"diaries\"],\"uploadIntervalSeconds\":300}"));
    assertEquals(Arrays.asList("skills", "diaries"), m.capabilities());
    assertEquals(300, m.uploadIntervalSeconds());
    assertTrue(m.allows("skills"));
    assertFalse(m.allows("quests"));
  }

  @Test
  public void rejectsAWholeManifestOnAnUnknownSchemaVersion() {
    // The version gates the document, not a field. A manifest written against a contract
    // this build does not speak cannot be partly understood, and honouring the parts that
    // look familiar is how a client ends up applying half of something.
    assertNull(
        CapabilityManifest.validate(
            parse("{\"schemaVersion\":2,\"capabilities\":[\"skills\"],\"uploadIntervalSeconds\":300}")));
    assertNull(
        CapabilityManifest.validate(
            parse("{\"capabilities\":[\"skills\"],\"uploadIntervalSeconds\":300}")));
  }

  @Test
  public void dropsAnUnknownCapabilityAndKeepsTheRest() {
    // Deliberately the opposite of the version gate. A new capability is an additive
    // change the server is entitled to make, and rejecting the whole manifest over one
    // would mean no capability could ever be introduced without stranding older clients.
    CapabilityManifest m =
        CapabilityManifest.validate(
            parse(
                "{\"schemaVersion\":1,\"capabilities\":[\"skills\",\"bankValue\",\"quests\"],"
                    + "\"uploadIntervalSeconds\":300}"));
    assertEquals(Arrays.asList("skills", "quests"), m.capabilities());
    assertFalse(m.allows("bankValue"));
  }

  @Test
  public void refusesTheCapabilitiesTheProductPromisedNeverToTake() {
    // Named explicitly because these are the ones the product states it will not capture.
    // They are unreachable twice over: not on the allowlist, and not expressible in the
    // wire type at all.
    CapabilityManifest m =
        CapabilityManifest.validate(
            parse(
                "{\"schemaVersion\":1,\"capabilities\":[\"bank\",\"inventory\",\"equipment\","
                    + "\"chat\",\"location\",\"skills\"],\"uploadIntervalSeconds\":300}"));
    assertEquals(Arrays.asList("skills"), m.capabilities());
    for (String denied : new String[] {"bank", "inventory", "equipment", "chat", "location"}) {
      assertFalse(denied, m.allows(denied));
      assertFalse(denied, CapabilityManifest.SUPPORTED_CAPABILITIES.contains(denied));
    }
  }

  @Test
  public void holdsTheUploadIntervalBetweenItsOwnFloorAndCeiling() {
    // A server able to drive this to zero could turn every paired client into a load
    // generator against Bankstand and against Wise Old Man. The client keeps the floor.
    assertEquals(
        CapabilityManifest.MIN_UPLOAD_INTERVAL_SECONDS, CapabilityManifest.clampInterval(0));
    assertEquals(
        CapabilityManifest.MIN_UPLOAD_INTERVAL_SECONDS, CapabilityManifest.clampInterval(-9999));
    assertEquals(
        CapabilityManifest.MIN_UPLOAD_INTERVAL_SECONDS, CapabilityManifest.clampInterval(1));
    assertEquals(
        CapabilityManifest.MAX_UPLOAD_INTERVAL_SECONDS,
        CapabilityManifest.clampInterval(Integer.MAX_VALUE));
    assertEquals(300, CapabilityManifest.clampInterval(300));
  }

  @Test
  public void ignoresFieldsItDoesNotKnow() {
    // What lets the server add a field without stranding older clients. Gson simply has
    // nowhere to put them, which is the behaviour we want and worth pinning.
    CapabilityManifest m =
        CapabilityManifest.validate(
            parse(
                "{\"schemaVersion\":1,\"capabilities\":[\"skills\"],\"uploadIntervalSeconds\":300,"
                    + "\"somethingNew\":\"ignored\",\"another\":{\"nested\":true}}"));
    assertEquals(Arrays.asList("skills"), m.capabilities());
  }

  @Test
  public void refusesAnAbsurdlyLongCapabilityList() {
    StringBuilder names = new StringBuilder();
    for (int i = 0; i < 200; i++) {
      names.append(i == 0 ? "" : ",").append("\"skills\"");
    }
    assertNull(
        CapabilityManifest.validate(
            parse(
                "{\"schemaVersion\":1,\"capabilities\":["
                    + names
                    + "],\"uploadIntervalSeconds\":300}")));
  }

  @Test
  public void namesEachCapabilityOnce() {
    CapabilityManifest m =
        CapabilityManifest.validate(
            parse(
                "{\"schemaVersion\":1,\"capabilities\":[\"skills\",\"skills\",\"skills\"],"
                    + "\"uploadIntervalSeconds\":300}"));
    assertEquals(Arrays.asList("skills"), m.capabilities());
  }

  @Test
  public void survivesRubbish() {
    // Every failure resolves to "keep what you have", never to an exception. A manifest
    // outage must not stop a paired client working.
    assertNull(CapabilityManifest.validate(null));
    assertNull(CapabilityManifest.validate(parse("{}")));
    assertNull(CapabilityManifest.validate(parse("{\"schemaVersion\":1}")));
    assertNull(
        CapabilityManifest.validate(parse("{\"schemaVersion\":1,\"capabilities\":null}")));
  }

  @Test
  public void fallsBackToEverythingThisBuildSupports() {
    // Before the first fetch, and after a total failure. The server's copy can only ever
    // narrow this, never widen it.
    CapabilityManifest bundled = CapabilityManifest.bundled();
    assertEquals(
        CapabilityManifest.SUPPORTED_CAPABILITIES.size(), bundled.capabilities().size());
    for (String name : CapabilityManifest.SUPPORTED_CAPABILITIES) {
      assertTrue(name, bundled.allows(name));
    }
    assertTrue(
        bundled.uploadIntervalSeconds() >= CapabilityManifest.MIN_UPLOAD_INTERVAL_SECONDS);
  }

  @Test
  public void describesItselfForTheDebugOutput() {
    // The active manifest should never be a guess when someone is reading a bug report.
    String line = CapabilityManifest.bundled().describe();
    assertTrue(line, line.contains("skills"));
    assertTrue(line, line.contains("v1"));
  }

  @Test
  public void everyCapabilityTheClientEverGatesOnIsInTheAllowlist() {
    // Every string BankstandPlugin ever passes to manifest.allows(...). Kept as a
    // literal list here, not derived, so this test does not accidentally validate
    // itself: it must independently know what the plugin gates on.
    List<String> gatedOn =
        Arrays.asList(
            "skills", "quests", "diaries", "collectionLog", "combatAchievements",
            "accountType", "notableDrops", "petDrops");
    for (String capability : gatedOn) {
      assertTrue(
          capability + " is gated on by the plugin but missing from SUPPORTED_CAPABILITIES,"
              + " so manifest.allows(" + capability + ") can never return true",
          CapabilityManifest.SUPPORTED_CAPABILITIES.contains(capability));
    }
  }
}
