package com.bankstand;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * The plugin stands alone. It never depends on another plugin, never touches another plugin's
 * config, and never trades PluginMessages with one. Another plugin can change or vanish in any
 * Hub update, and its config is its users' data, not ours.
 *
 * <p>If a PluginMessage integration is ever wanted, add its namespace to {@link
 * #ALLOWED_MESSAGE_NAMESPACES} in the same change that adds a version check on the payload, so a
 * format change on the other side is ignored rather than misread. Comments are exempt.
 */
public class PluginIsolationTest {

  private static final Path SOURCE_ROOT = Paths.get("src", "main", "java");

  private static final Set<String> ALLOWED_MESSAGE_NAMESPACES = new HashSet<>(Arrays.asList());

  private static final Set<String> OWN_GROUP =
      new HashSet<>(Arrays.asList("BankstandKeys.GROUP", "\"" + BankstandKeys.GROUP + "\""));

  private static final Pattern CONFIG_CALL =
      Pattern.compile(
          "\\b(?:get|set|unset)(?:RSProfile)?Configuration(?:Keys)?\\s*\\(\\s*([^,)]+)");
  private static final Pattern CONFIG_GROUP_ANNOTATION =
      Pattern.compile("@ConfigGroup\\s*\\(\\s*([^)]+)\\)");
  private static final Pattern GET_CONFIG = Pattern.compile("\\bgetConfig\\s*\\(\\s*([^)]+)\\)");
  private static final Pattern[] MESSAGE_NAMESPACE = {
    Pattern.compile("new\\s+PluginMessage\\s*\\(\\s*\"([^\"]*)\""),
    Pattern.compile("\"([^\"]*)\"\\s*\\.equals\\s*\\(\\s*[\\w.]*getNamespace\\(\\)"),
    Pattern.compile("getNamespace\\(\\)\\s*\\.equals\\s*\\(\\s*\"([^\"]*)\""),
  };

  @Test
  public void noPluginDependency() throws IOException {
    List<String> offences = new ArrayList<>();
    for (Source s : sources()) {
      if (s.code.contains("@PluginDependency")) {
        offences.add(s.path.toString());
      }
    }
    assertNone("Never depend on another plugin:", offences);
  }

  @Test
  public void configOnlyTouchesOwnGroup() throws IOException {
    List<String> offences = new ArrayList<>();
    for (Source s : sources()) {
      for (Pattern p : new Pattern[] {CONFIG_CALL, CONFIG_GROUP_ANNOTATION}) {
        Matcher m = p.matcher(s.code);
        while (m.find()) {
          if (!OWN_GROUP.contains(m.group(1).trim())) {
            offences.add(s.path + "  " + m.group().trim());
          }
        }
      }
      Matcher m = GET_CONFIG.matcher(s.code);
      while (m.find()) {
        if (!m.group(1).trim().equals("BankstandConfig.class")) {
          offences.add(s.path + "  " + m.group().trim());
        }
      }
    }
    assertNone(
        "Only read or write the '" + BankstandKeys.GROUP + "' config group:", offences);
  }

  @Test
  public void pluginMessagesNeedAllowedNamespaceAndVersionCheck() throws IOException {
    List<String> offences = new ArrayList<>();
    for (Source s : sources()) {
      if (!s.code.contains("PluginMessage")) {
        continue;
      }
      List<String> namespaces = new ArrayList<>();
      for (Pattern p : MESSAGE_NAMESPACE) {
        Matcher m = p.matcher(s.code);
        while (m.find()) {
          namespaces.add(m.group(1));
        }
      }
      if (namespaces.isEmpty()) {
        offences.add(s.path + "  uses PluginMessage with no literal namespace");
      }
      for (String ns : namespaces) {
        if (!ALLOWED_MESSAGE_NAMESPACES.contains(ns)) {
          offences.add(s.path + "  namespace not allowed: " + ns);
        }
      }
      if (!s.code.contains("\"version\"")) {
        offences.add(s.path + "  uses PluginMessage without a version check");
      }
    }
    assertNone("PluginMessage needs an allowed namespace and a version check:", offences);
  }

  private static void assertNone(String message, List<String> offences) {
    assertTrue(message + "\n" + String.join("\n", offences), offences.isEmpty());
  }

  private static final class Source {
    final Path path;
    final String code;

    Source(Path path, String code) {
      this.path = path;
      this.code = code;
    }
  }

  private static List<Source> sources() throws IOException {
    List<Source> out = new ArrayList<>();
    try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
      for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        out.add(new Source(file, stripComments(text)));
      }
    }
    return out;
  }

  private static String stripComments(String text) {
    return text.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)^\\s*//.*$", "");
  }
}
