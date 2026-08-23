package com.bankstand.dto;

/**
 * The response from {@code POST /api/plugin/v1/submit}: whether the character was
 * matched to one of the user's tracked accounts and linked ({@code verified}), the
 * display name it linked to ({@code linkedRsn}, null when nothing matched), and why
 * ({@code outcome}). Populated by Gson.
 *
 * <p>{@code outcome} is additive: a server predating it never sends the field, and
 * Gson leaves it null rather than failing the parse, so an older server keeps
 * working exactly as it did before the field existed.
 */
public class SubmitResponse {
  private boolean verified;
  private String linkedRsn;
  private String outcome;

  public boolean isVerified() {
    return verified;
  }

  public String getLinkedRsn() {
    return linkedRsn;
  }

  public String getOutcome() {
    return outcome;
  }
}
