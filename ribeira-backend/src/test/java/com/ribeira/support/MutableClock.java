package com.ribeira.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** Clock that tests can move forward; defaults to the real time at creation. */
public class MutableClock extends Clock {

  private final AtomicReference<Instant> now = new AtomicReference<>(Instant.now());

  public void advance(Duration duration) {
    now.updateAndGet(current -> current.plus(duration));
  }

  public void set(Instant instant) {
    now.set(instant);
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return this;
  }

  @Override
  public Instant instant() {
    return now.get();
  }
}
