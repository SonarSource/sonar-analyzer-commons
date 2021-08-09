SonarSource Analyzers Commons (compatible with SQ >=7.9)
=========================
Logic useful for an average language plugin

* [`RuleMetadataLoader`](./src/main/java/org/sonarsource/analyzer/commons/RuleMetadataLoader.java) - to define rules metadata based on `json` and `html` files
* [`DeprecatedRuleKey`](./src/main/java/org/sonarsource/analyzer/commons/annotations/DeprecatedRuleKey.java) annotation - when used with [`RuleMetadataLoader`](./src/main/java/org/sonarsource/analyzer/commons/RuleMetadataLoader.java) in will add deprecated rule key for an annotated rule
* [`BuiltInQualityProfileJsonLoader`](./src/main/java/org/sonarsource/analyzer/commons/BuiltInQualityProfileJsonLoader.java) - to define default rules profiles based on `json` file
* [`ProfileGenerator`](./src/main/java/org/sonarsource/analyzer/commons/ProfileGenerator.java) - to generate rules profile `xml` file (e.g. can be used for integration tests)
* [`TokenLocation`](./src/main/java/org/sonarsource/analyzer/commons/TokenLocation.java) - to compute token location
* [`ExternalRuleLoader`](./src/main/java/org/sonarsource/analyzer/commons/ExternalRuleLoader.java) - to load external rules descriptions from `json` file
* [`ExternalReportProvider`](./src/main/java/org/sonarsource/analyzer/commons/ExternalReportProvider.java) - to get the list of io.File with external reports
* [`ProgressReport`](./src/main/java/org/sonarsource/analyzer/commons/ProgressReport.java) - to produce logs with number of analyzed files
* [`FileProvider`](./src/main/java/org/sonarsource/analyzer/commons/FileProvider.java) - to get files matching given pattern in the given directory

### License
Copyright 2009-2021 SonarSource.
Licensed under the [GNU Lesser General Public License, Version 3.0](http://www.gnu.org/licenses/lgpl.txt)
