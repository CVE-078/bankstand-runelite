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
    // The wire type has no field that could carry a varbit, script, widget, URL, class name
    // or expression, so a compromised server cannot widen what the plugin reads.
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
    // An unknown version rejects the whole document; half-applying a manifest is unsafe.
    assertNull(
        CapabilityManifest.validate(
            parse("{\"schemaVersion\":2,\"capabilities\":[\"skills\"],\"uploadIntervalSeconds\":300}")));
    assertNull(
        CapabilityManifest.validate(
            parse("{\"capabilities\":[\"skills\"],\"uploadIntervalSeconds\":300}")));
  }

  @Test
  public void dropsAnUnknownCapabilityAndKeepsTheRest() {
    // Unlike the version gate, an unknown capability is ignored so the server can add one.
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
    // The client keeps a floor so the server cannot turn clients into a load generator.
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
    // Every failure resolves to "keep what you have", never an exception.
    assertNull(CapabilityManifest.validate(null));
    assertNull(CapabilityManifest.validate(parse("{}")));
    assertNull(CapabilityManifest.validate(parse("{\"schemaVersion\":1}")));
    assertNull(
        CapabilityManifest.validate(parse("{\"schemaVersion\":1,\"capabilities\":null}")));
  }

  @Test
  public void fallsBackToEverythingThisBuildSupports() {
    // The server's copy can only narrow the bundled one, never widen it.
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
    String line = CapabilityManifest.bundled().describe();
    assertTrue(line, line.contains("skills"));
    assertTrue(line, line.contains("v1"));
  }

  @Test
  public void everyCapabilityTheClientEverGatesOnIsInTheAllowlist() {
    // Literal, not derived, so the test does not validate itself.
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
