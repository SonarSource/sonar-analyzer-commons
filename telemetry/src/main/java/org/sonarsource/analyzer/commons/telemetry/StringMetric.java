package org.sonarsource.analyzer.commons.telemetry;

import javax.annotation.Nullable;

public class StringMetric extends AbstractMetric{

  private String value;

  public StringMetric(String key) {
    this(key, "");
  }

  public StringMetric(String key, String value) {
    super(key);
    this.value = value;
  }

  public void set(String value) {
    this.value = value;
  }

  public void concat(String additionalValue) {
    if (this.value == null) {
      this.value = additionalValue;
    } else {
      this.value += additionalValue;
    }
  }

  @Override
  public String value() {
    return value;
  }
}
