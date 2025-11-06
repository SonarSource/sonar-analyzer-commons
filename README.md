# SonarSource Analyzers Commons Libraries

[![Build](https://github.com/SonarSource/sonar-analyzer-commons/actions/workflows/build.yml/badge.svg)](https://github.com/SonarSource/sonar-analyzer-commons/actions/workflows/build.yml)

## Modules

* [commons](commons) Logic useful for a language plugin
* [recognizers](recognizers) Logic useful for detecting commented out code
* [test-commons](test-commons) Logic useful to test a language analyzer
* [xml-parsing](xml-parsing) Logic useful to analyze and test checks for XML file
* [test-xml-parsing](test-xml-parsing) Logic useful to test XML parsing and XML-related rules
* [regex-parsing](regex-parsing) Logic used to parse regular expressions (currently only for Java)

## Build
```
mvn clean install
```

### License
Copyright 2009-2025 SonarSource.
Licensed under the [GNU Lesser General Public License, Version 3.0](http://www.gnu.org/licenses/lgpl.txt)
