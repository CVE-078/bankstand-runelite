package com.bankstand;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

/** The version the server sees must be the version that was built and listed. */
public class PluginVersionTest {

  private static String find(String file, String regex) throws IOException {
    String text = new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8);
    Matcher m = Pattern.compile(regex, Pattern.MULTILINE).matcher(text);
    assertTrue(file + " carries a version", m.find());
    return m.group(1);
  }

  @Test
  public void versionMatchesTheBuild() throws IOException {
    assertEquals(find("build.gradle", "^version = '([^']+)'"), PluginVersion.VERSION);
  }

  @Test
  public void versionMatchesTheHubListing() throws IOException {
    assertEquals(find("runelite-plugin.properties", "^version=(\\S+)"), PluginVersion.VERSION);
  }

  @Test
  public void envelopeAndUserAgentUseTheConstant() throws IOException {
    String plugin =
        new String(
            Files.readAllBytes(Paths.get("src/main/java/com/bankstand/BankstandPlugin.java")),
            StandardCharsets.UTF_8);
    assertTrue(plugin.contains("PluginVersion.VERSION"));
    assertTrue(!plugin.contains("getImplementationVersion"));
  }
}
