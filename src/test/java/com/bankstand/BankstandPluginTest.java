package com.bankstand;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Developer-mode entry point: launches RuneLite with this plugin sideloaded. Lives
 * in the test source set so it is never part of the shipped plugin. Run it with
 * {@code ./gradlew run}.
 */
public class BankstandPluginTest {
  public static void main(String[] args) throws Exception {
    ExternalPluginManager.loadBuiltin(BankstandPlugin.class);
    RuneLite.main(args);
  }
}
