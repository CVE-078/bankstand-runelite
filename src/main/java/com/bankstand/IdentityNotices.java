package com.bankstand;

/** The chat lines for an identity check's answer or failure. */
final class IdentityNotices {
  private IdentityNotices() {}

  /**
   * The chat line for an identity submit's result. Not-claimed and held-by-another-account need
   * different fixes, so they read differently. {@code outcome} is optional on the wire; a
   * missing or unknown value falls back to the not-claimed line.
   */
  static String identityNoticeFor(boolean verified, String linkedRsn, String outcome) {
    if (verified) {
      return "Verified as " + linkedRsn + ".";
    }
    if ("held_by_other".equals(outcome) || "hash_bound_elsewhere".equals(outcome)) {
      return "This character is already linked, so there is nothing to claim here."
          + " If that looks wrong, contact support.";
    }
    return "This character is not claimed on Bankstand yet, so nothing will sync."
        + " Claim it on your account page.";
  }

  /** Null when already announced: a rejected token halts and is told once until re-pairing. */
  static String identityFailureNotice(
      Throwable failure, SubmitGate submitGate, NoticeGate noticeGate) {
    boolean auth = failure instanceof SubmitException && ((SubmitException) failure).isAuthFailure();
    boolean announce =
        auth ? submitGate.onAuthFailure() : noticeGate.onFailure(failure.getMessage());
    return announce ? "Could not verify this character. " + failure.getMessage() : null;
  }
}
