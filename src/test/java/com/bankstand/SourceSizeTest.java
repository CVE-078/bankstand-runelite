package com.bankstand;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.Test;

/** Keeps every source file small enough to review in one sitting. */
public class SourceSizeTest {

  private static final Path SOURCE_ROOT = Paths.get("src", "main", "java");

  private static final int MAX_LINES = 400;

  /** Static data and the config interface grow with the game, not with logic. */
  private static final Set<String> EXEMPT =
      Set.of("com/bankstand/BankstandConfig.java");

  /** Files still over the limit. Lower an entry when its file shrinks; never raise one. */
  private static final Map<String, Integer> RATCHET = new HashMap<>();

  static {
    RATCHET.put("com/bankstand/BankstandPlugin.java", 2581);
    RATCHET.put("com/bankstand/BankstandClient.java", 432);
  }

  @Test
  public void noSourceFileGrowsPastItsLimit() throws IOException {
    List<String> offences = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
      for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
        String name = SOURCE_ROOT.relativize(file).toString().replace('\\', '/');
        seen.add(name);
        if (EXEMPT.contains(name)) {
          continue;
        }
        int lines = Files.readAllLines(file, StandardCharsets.UTF_8).size();
        Integer ceiling = RATCHET.get(name);
        if (ceiling == null && lines > MAX_LINES) {
          offences.add(file + " has " + lines + " lines, over the " + MAX_LINES + " limit");
        } else if (ceiling != null && lines > ceiling) {
          offences.add(file + " grew to " + lines + " lines, past its ratchet of " + ceiling);
        } else if (ceiling != null && lines < ceiling) {
          offences.add(file + " shrank to " + lines + " lines: lower its ratchet to match");
        }
      }
    }
    for (String name : RATCHET.keySet()) {
      if (!seen.contains(name)) {
        offences.add(name + " has a ratchet entry but no longer exists: remove the entry");
      }
    }
    for (String name : EXEMPT) {
      if (!seen.contains(name)) {
        offences.add(name + " is exempt but no longer exists: remove the exemption");
      }
    }
    assertTrue(String.join("\n", offences), offences.isEmpty());
  }
}
