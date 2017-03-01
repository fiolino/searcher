package org.fiolino.searcher;

import org.fiolino.common.util.Strings;

import java.util.concurrent.TimeUnit;

/**
 * Created by kuli on 25.11.16.
 */
public class Measurement {
  private final long start = System.nanoTime();
  private long queryBuilt;
  private long queryReturned;
  private long resultFinished;

  public void queryBuilt() {
    if (queryBuilt > 0) {
      throw new IllegalStateException("querySent called twice!");
    }
    queryBuilt = System.nanoTime();
  }

  public void queryReturned() {
    if (queryBuilt == 0) {
      throw new IllegalStateException("querySent not called yet!");
    }
    if (queryReturned > 0) {
      throw new IllegalStateException("queryReturned called twice!");
    }
    queryReturned = System.nanoTime();
  }

  public void resultFinished() {
    if (queryBuilt == 0) {
      throw new IllegalStateException("querySent not called yet!");
    }
    if (queryReturned == 0) {
      throw new IllegalStateException("queryReturned not called yet!");
    }
    if (resultFinished > 0) {
      throw new IllegalStateException("resultFinished called twice!");
    }
    resultFinished = System.nanoTime();
  }

  public String measureAll(long qTime) {
    resultFinished();
    return "Time measurement: build query " + Strings.printDuration(queryBuilt - start, TimeUnit.NANOSECONDS)
            + "; server query time " + qTime
            + " msec; query returned " + Strings.printDuration(queryReturned - start, TimeUnit.MILLISECONDS)
            + "; result finished " + Strings.printDuration(resultFinished - start, TimeUnit.MILLISECONDS);
  }

  @Override
  public String toString() {
    if (resultFinished == 0) {
      return "Started " + Strings.printDuration(System.nanoTime() - start, TimeUnit.NANOSECONDS) + " ago.";
    }
    return "Time measurement: build query " + Strings.printDuration(queryBuilt - start, TimeUnit.NANOSECONDS)
            + "; query returned " + Strings.printDuration(queryReturned - start, TimeUnit.MILLISECONDS)
            + "; result finished " + Strings.printDuration(resultFinished - start, TimeUnit.MILLISECONDS);
  }
}
