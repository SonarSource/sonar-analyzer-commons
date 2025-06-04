package org.sonarsource.analyzer.commons.telemetry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DurationMetricTest {

  @Test
  void testStartAndStop() {
    DurationMetric metric = new DurationMetric("testDuration");
    assertFalse(metric.isStarted());
    assertFalse(metric.isStopped());
    assertNull(metric.value());

    metric.start();
    assertTrue(metric.isStarted());
    assertFalse(metric.isStopped());
    assertNull(metric.value());

    metric.stop();
    assertTrue(metric.isStarted());
    assertTrue(metric.isStopped());
    assertNotNull(metric.value());
  }

  @Test
  void testValue() {
    DurationMetric metric = new DurationMetric("testDuration");
    metric.start();
    try {
      Thread.sleep(100); // Simulate some processing time
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    metric.stop();

    String value = metric.value();
    assertNotNull(value);
    assertTrue(Long.parseLong(value) >= 100); // Ensure the duration is at least 100ms
  }
}
