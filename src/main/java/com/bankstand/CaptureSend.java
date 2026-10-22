package com.bankstand;

import java.util.List;
import java.util.Map;

/** A request's token, queue and tickets, read in one step so all belong to one pairing. */
final class CaptureSend<T> {
  final String token;
  final List<T> pending;
  private final Map<Long, CaptureBoundary.Ticket> asked;

  CaptureSend(String token, List<T> pending, Map<Long, CaptureBoundary.Ticket> asked) {
    this.token = token;
    this.pending = pending;
    this.asked = asked;
  }

  CaptureBoundary.Ticket asked(long account) {
    return asked.get(account);
  }
}
