package com.bankstand.dto;

/**
 * The response from {@code POST /api/plugin/v1/submit}: whether the character was
 * matched to one of the user's tracked accounts and linked ({@code verified}), and
 * the display name it linked to ({@code linkedRsn}, null when nothing matched).
 * Populated by Gson.
 */
public class SubmitResponse {
  private boolean verified;
  private String linkedRsn;

  public boolean isVerified() {
    return verified;
  }

  public String getLinkedRsn() {
    return linkedRsn;
  }
}
