# Performance measurements

`PerformanceMeasure` starts and stops nested duration measurements.
`DurationMeasure` stores the hierarchy. `DurationMeasureFiles` reads and writes
reports. `DurationMeasureMerger` combines measurements. `log/` adapts logging.
The README shows the sensor and report flow.

Preserve disabled-mode behavior, nesting, thread behavior, and report format.
Do not make correctness tests depend on a fixed wall-clock duration. Use the
adjacent measurement and merger tests for the behavior under change.

Run `mise run test performance-measure` from the root. No running
SonarQube service is needed for these unit tests.
