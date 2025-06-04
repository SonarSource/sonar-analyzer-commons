package org.sonarsource.analyzer.commons.telemetry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StringMetricTest {

  @Test
  void testSet() {
    StringMetric metric = new StringMetric("testString");
    assertNull(metric.value());

    metric.set("Hello");
    assertEquals("Hello", metric.value());

    metric.set("World");
    assertEquals("World", metric.value());
  }

  @Test
  void testConcat() {
    StringMetric metric = new StringMetric("testString");
    assertNull(metric.value());

    metric.concat("Hello");
    assertEquals("Hello", metric.value());

    metric.concat(" World");
    assertEquals("Hello World", metric.value());

    metric.set(null);
    metric.concat("New Value");
    assertEquals("New Value", metric.value());
  }

}
