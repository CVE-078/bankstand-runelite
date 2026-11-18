package com.bankstand;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/**
 * One request's worth of queued captures: same account, same capture generation. A request
 * carries one generation, so entries queued under different ones are sent apart.
 */
final class SendGroup {
  final long accountHash;
  final Integer captureGeneration;

  SendGroup(long accountHash, Integer captureGeneration) {
    this.accountHash = accountHash;
    this.captureGeneration = captureGeneration;
  }

  /** Groups queued entries, keeping first-seen group order and entry order within a group. */
  static <E, T> Map<SendGroup, List<T>> of(
      List<E> entries,
      ToLongFunction<E> account,
      Function<E, Integer> generation,
      Function<E, T> item) {
    Map<SendGroup, List<T>> groups = new LinkedHashMap<>();
    for (E entry : entries) {
      groups
          .computeIfAbsent(
              new SendGroup(account.applyAsLong(entry), generation.apply(entry)),
              key -> new ArrayList<>())
          .add(item.apply(entry));
    }
    return groups;
  }

  /** Splits into batches of at most {@code maxSize}, oldest first. */
  static <T> List<List<T>> chunk(List<T> items, int maxSize) {
    List<List<T>> chunks = new ArrayList<>();
    for (int start = 0; start < items.size(); start += maxSize) {
      chunks.add(new ArrayList<>(items.subList(start, Math.min(start + maxSize, items.size()))));
    }
    return chunks;
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof SendGroup)) {
      return false;
    }
    SendGroup that = (SendGroup) other;
    return accountHash == that.accountHash
        && Objects.equals(captureGeneration, that.captureGeneration);
  }

  @Override
  public int hashCode() {
    return Objects.hash(accountHash, captureGeneration);
  }
}
