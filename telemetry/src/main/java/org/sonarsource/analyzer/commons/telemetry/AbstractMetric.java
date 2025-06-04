package org.sonarsource.analyzer.commons.telemetry;

public abstract class AbstractMetric implements Metric {

  private final String key;

  protected AbstractMetric(String key) {
    this.key = key;
  }

  @Override
  public String key() {
    return key;
  }
}
