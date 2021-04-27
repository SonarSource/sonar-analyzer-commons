# SonarSource Performance Measure Library

## Usage

### Measure
Prepare the root hierarchy of the performance measure in the Sensor:
```
  public void execute(SensorContext context) {
    PerformanceMeasure.Duration durationReport = PerformanceMeasure.reportBuilder()
      .activate(/* ... provide a value from context.config() to decide if the peformance measurement is active ... */)
      .toFile(/* ... path for the generated report, can be read from context.config() or defaulted in
                     context.fileSystem().workDir(), if null only the debug log will contains the report ... */)
      .allowSingleThreadMode() // Speedup the measure when the analysis is not multithreaded
      .appendMeasurementCost()
      .start("ZuluSensor");
    // sensor code
    // ...
    durationReport.stop();
  }
```

Add measure at several locations in the code: 
```
  public Tree parser(String code) {
    PerformanceMeasure.Duration duration = PerformanceMeasure.start("Parser");
    // ...
    duration.stop();
  }
```

```
  public void scan(Tree tree, Check check) {
    PerformanceMeasure.Duration duration = PerformanceMeasure.start(check);
    // ...
    duration.stop();
  }
```

### Ruling
During the ruling, it's possible to generate a more human-readable report:
```
    Map<String, String> categoryNames = new HashMap<>();
    categoryNames.put("SymbolicExecutionVisitor", "symbolic-execution");
    Predicate<String> groupedMeasurePredicate = name -> name.endsWith("Check");

    DurationMeasure measure = DurationMeasureFiles.fromJsonWithoutObservationCost(performanceJsonFile);
    measure.recursiveMergeOnUpperLevel("ComputationOfCachedValue");
    Path performanceStatFile = performanceDirectory.resolve("zulu.performance.statistics.txt");
    DurationMeasureFiles.writeStatistics(performanceStatFile, measure, categoryNames, groupedMeasurePredicate);
```
