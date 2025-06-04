package org.sonarsource.analyzer.commons.telemetry;

import javax.annotation.CheckForNull;

public interface Metric {

  @CheckForNull
  String key();

  @CheckForNull
  String value();
}
