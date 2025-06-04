package org.sonarsource.analyzer.commons.telemetry;

public class NumericMetric extends AbstractMetric {

  private float value;


  public NumericMetric(String key) {
    this(key, 0.0f);
  }

  public NumericMetric(String key, float value) {
    super(key);
    this.value = value;
  }

  public void add(long value) {
    this.value += value;
  }

  public void add(int value) {
    this.value += value;
  }

  public void add(float value) {
    this.value += value;
  }

  public String value() {
    return String.valueOf(value);
  }
}
