# sonar-analyzer-commons
Logic useful for an average language plugin

* [`RuleMetadataLoader`](commons/src/main/java/org/sonarsource/analyzer/commons/RuleMetadataLoader.java) - to define rules metadata based on `json` and `html` files
* [`BuiltInQualityProfileJsonLoader`](commons/src/main/java/org/sonarsource/analyzer/commons/BuiltInQualityProfileJsonLoader.java) - to define default rules profiles based on `json` file
* [`ProfileGenerator`](commons/src/main/java/org/sonarsource/analyzer/commons/ProfileGenerator.java) - to generate rules profile `xml` file (e.g. can be used for integration tests)
* [`TokenLocation`](commons/src/main/java/org/sonarsource/analyzer/commons/TokenLocation.java) - to compute token location
* [`ExternalRuleLoader`](commons/src/main/java/org/sonarsource/analyzer/commons/ExternalRuleLoader.java) - to load external rules descriptions from `json` file
* [`ExternalReportProvider`](commons/src/main/java/org/sonarsource/analyzer/commons/ExternalReportProvider.java) - to get the list of io.File with external reports
* [`ProgressReport`](commons/src/main/java/org/sonarsource/analyzer/commons/ProgressReport.java) - to produce logs with number of analyzed files

### License
Copyright 2009-2017 SonarSource.
Licensed under the [GNU Lesser General Public License, Version 3.0](http://www.gnu.org/licenses/lgpl.txt)
