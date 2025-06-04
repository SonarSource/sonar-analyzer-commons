package org.sonarsource.analyzer.commons.telemetry;

public class DurationMetric extends AbstractMetric {

  private long startTime;
  private long endTime;

  public DurationMetric(String key) {
    super(key);
  }

  public void start() {
    this.startTime = System.currentTimeMillis();
  }

  public void stop() {
    this.endTime = System.currentTimeMillis();
  }

  public boolean isStarted() {
    return startTime > 0;
  }

  public boolean isStopped() {
    return endTime > 0;
  }

  public String value() {
    if (startTime == 0 || endTime == 0) {
      return null;
    }
    return String.valueOf(endTime - startTime);
  }

}
