package com.bankstand;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** The build's version, stamped into {@code version.properties} by Gradle. */
final class PluginVersion {

  static final String VERSION = load();

  private PluginVersion() {}

  private static String load() {
    try (InputStream in = PluginVersion.class.getResourceAsStream("version.properties")) {
      if (in == null) {
        return "unknown";
      }
      Properties props = new Properties();
      props.load(in);
      return props.getProperty("version", "unknown");
    } catch (IOException e) {
      return "unknown";
    }
  }
}
