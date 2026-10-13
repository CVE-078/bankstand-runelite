package com.bankstand;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.Test;

/** Keeps every source file small enough to review in one sitting. */
public class SourceSizeTest {

  private static final Path SOURCE_ROOT = Paths.get("src", "main", "java");

  private static final int MAX_LINES = 400;

  /** Static data and the config interface grow with the game, not with logic. */
  private static final String[] EXEMPT = {"DiaryTaskManifestData.java", "BankstandConfig.java"};

  /** Files still over the limit. Lower an entry when its file shrinks; never raise one. */
  private static final Map<String, Integer> RATCHET = new HashMap<>();

  static {
    RATCHET.put("BankstandPlugin.java", 2671);
    RATCHET.put("BankstandClient.java", 469);
  }

  @Test
  public void noSourceFileGrowsPastItsLimit() throws IOException {
    List<String> offences = new ArrayList<>();
    try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
      for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
        String name = file.getFileName().toString();
        if (isExempt(name)) {
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
    assertTrue(String.join("\n", offences), offences.isEmpty());
  }

  private static boolean isExempt(String name) {
    for (String exempt : EXEMPT) {
      if (exempt.equals(name)) {
        return true;
      }
    }
    return false;
  }
}
