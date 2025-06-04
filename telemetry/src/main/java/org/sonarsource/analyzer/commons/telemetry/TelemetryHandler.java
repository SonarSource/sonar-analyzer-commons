package org.sonarsource.analyzer.commons.telemetry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.utils.Version;

import java.util.ArrayList;
import java.util.List;

public class TelemetryHandler {

  public static final Version TELEMETRY_SUPPORTED_API_VERSION = Version.create(10, 9);

  protected final List<Metric> metrics = new ArrayList<>();

  private final SensorContext ctx;
  private final Logger logger;
  private final String prefix;

  public TelemetryHandler(SensorContext ctx, String prefix) {
    this(ctx, prefix, LoggerFactory.getLogger(TelemetryHandler.class));
  }

  public TelemetryHandler(SensorContext ctx, String prefix, Logger logger) {
    this.ctx = ctx;
    this.prefix = prefix;
    this.logger = logger;
  }

  public void addMetric(Metric metric) {
    metrics.add(metric);
  }

  public void addMetrics(List<Metric> metrics) {
    this.metrics.addAll(metrics);
  }

  public void report() {
    if (!isTelemetrySupported()) {
      logger.warn("Telemetry is not supported in this version of SonarQube. Metrics will not be reported.");
      return;
    }

    for (Metric metric : metrics) {
      if (metric == null || metric.key() == null || metric.value() == null) {
        logger.warn("Skipping telemetry metric due to null key or value: {}", metric);
        continue;
      }

      var prefixedKey = prefix + metric.key();
      try {
        ctx.addTelemetryProperty(prefixedKey, metric.value());
      } catch (Exception e) {
        logger.warn("Failed to add telemetry property: {} with value: {}. Error: {}", prefixedKey, metric.value(), e.getMessage());
      }
    }
  }

  private boolean isTelemetrySupported() {
    return ctx.runtime().getApiVersion().isGreaterThanOrEqual(TELEMETRY_SUPPORTED_API_VERSION);
  }
}
