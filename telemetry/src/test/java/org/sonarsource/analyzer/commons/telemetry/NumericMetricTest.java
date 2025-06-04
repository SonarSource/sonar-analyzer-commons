package org.sonarsource.analyzer.commons.telemetry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NumericMetricTest {

  @Test
  void testAdd() {
    NumericMetric metric = new NumericMetric("testKey");
    metric.add(5);
    assertEquals("5.0", metric.value());

    metric.add(3.5f);
    assertEquals("8.5", metric.value());

    metric.add(2L);
    assertEquals("10.5", metric.value());
  }

  @Test
  void testValue() {
    NumericMetric metric = new NumericMetric("testKey", 42.0f);
    assertEquals("42.0", metric.value());
  }

}
