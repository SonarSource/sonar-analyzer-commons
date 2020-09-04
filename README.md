# SonarSource Analyzers Commons Libraries

[![Build Status](https://api.cirrus-ci.com/github/SonarSource/sonar-analyzer-commons.svg?branch=master)](https://cirrus-ci.com/github/SonarSource/sonar-analyzer-commons) [![Quality Gate Status](https://next.sonarqube.com/sonarqube/api/project_badges/measure?project=org.sonarsource.analyzer-commons%3Asonar-analyzer-commons-parent&metric=alert_status)](https://next.sonarqube.com/sonarqube/dashboard?id=org.sonarsource.analyzer-commons%3Asonar-analyzer-commons-parent) [![Coverage](https://next.sonarqube.com/sonarqube/api/project_badges/measure?project=org.sonarsource.analyzer-commons%3Asonar-analyzer-commons-parent&metric=coverage)](https://next.sonarqube.com/sonarqube/dashboard?id=org.sonarsource.analyzer-commons%3Asonar-analyzer-commons-parent)

## Modules

* [commons](commons) Logic useful for a language plugin
* [recognizers](recognizers) Logic useful for detecting commented out code
* [test-commons](test-commons) Logic useful to test a language analyzer
* [xml-parsing](xml-parsing) Logic useful to analyze and test checks for XML file
* [test-xml-parsing](test-xml-parsing) Logic useful to test XML parsing and XML-related rules


## Build
```
mvn clean install
```

### License
Copyright 2009-2020 SonarSource.
Licensed under the [GNU Lesser General Public License, Version 3.0](http://www.gnu.org/licenses/lgpl.txt)
