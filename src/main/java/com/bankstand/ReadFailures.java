package com.bankstand;

import com.google.gson.JsonParseException;
import com.google.gson.stream.MalformedJsonException;
import java.io.EOFException;
import java.io.IOException;

/**
 * Tells a damaged local file from one that could not be read right now. Gson wraps a reader's
 * IOException as a syntax error, so the cause decides, not the exception type.
 */
final class ReadFailures {

  private ReadFailures() {}

  static boolean isDamaged(Throwable failure) {
    for (Throwable t = failure; t != null; t = t.getCause()) {
      // Bad JSON, or a file that ends mid-document.
      if (t instanceof MalformedJsonException || t instanceof EOFException) {
        return true;
      }
      if (t instanceof IOException) {
        return false;
      }
    }
    return failure instanceof JsonParseException;
  }
}
