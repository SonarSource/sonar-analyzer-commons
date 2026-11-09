<!-- Sonar Marketing hosts these approved brand assets on its Kentico Kontent CDN (assets-eu-01.kc-usercontent.com). Shared URLs are intentional; consult Marketing before replacing them. -->
<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://assets-eu-01.kc-usercontent.com/ef593040-b591-0198-9506-ed88b30bc023/a23fc7ba-23f0-489a-829d-ed88c0748521/Sonar_Logo_Dark%20Backgrounds.svg">
    <img src="https://assets-eu-01.kc-usercontent.com/ef593040-b591-0198-9506-ed88b30bc023/82c13eba-d95c-4bb8-8007-7ce77c14e043/Sonar_Logo_Light%20Backgrounds.svg" alt="Sonar logo" width="400">
  </picture>
</p>

[![Build](https://github.com/SonarSource/sonar-analyzer-commons/actions/workflows/build.yml/badge.svg?branch=master)](https://github.com/SonarSource/sonar-analyzer-commons/actions/workflows/build.yml)

<!-- sonar-marketing:start -->
<!-- Marketing maintains this section. For wording changes, consult the relevant Product Marketing Manager (PMM). Repository maintainers review accuracy and merge changes. -->

# Sonar analyzer common libraries

This repository contains shared Java libraries for Sonar language analyzers. Its modules provide common analyzer logic, test helpers, XML parsing, regular-expression parsing, and recognition of commented-out code.

To learn more about the SonarQube product family, visit the [Sonar website](https://www.sonarsource.com/products/sonarqube/).

<!-- sonar-marketing:end -->

## Modules

* [commons](commons) Logic useful for a language plugin
* [sonar-analyzer-commons-configurations](sonar-analyzer-commons-configurations) JSON, npm, and NuGet exports of shared configuration
* [recognizers](recognizers) Logic useful for detecting commented out code
* [test-commons](test-commons) Logic useful to test a language analyzer
* [xml-parsing](xml-parsing) Logic useful to analyze and test checks for XML file
* [test-xml-parsing](test-xml-parsing) Logic useful to test XML parsing and XML-related rules
* [regex-parsing](regex-parsing) Regular-expression parsing and shared checks, with Java, PHP, and Python source adapters
* [performance-measure](performance-measure) Nested duration measurements and report merging

## Build
Use JDK 21 or later and Maven 3.9 or later. [mise.toml](mise.toml) selects the
existing Java 21.0 and Maven 3.9 release lines.
Activate the chosen Java and Maven tools, then run:

```sh
mvn --version
mvn -B -ntp -C -Pcoverage verify
```

For a quick module test, run `mvn -pl recognizers -am test`.
For a full local install, run `mvn install`.
Python 3.9 or later is used only by the separate repository checks described in
[AGENTS.md](AGENTS.md); Maven builds and tests do not need it.
See [AGENTS.md](AGENTS.md) for module selection, failure diagnosis, source
boundaries, and contribution rules. The optional [.devcontainer](.devcontainer)
contains a development environment. CI signing and publishing require internal
services; local unit tests do not require them.

### License
Copyright 2009-2023 SonarSource.
Licensed under the [GNU Lesser General Public License, Version 3.0](http://www.gnu.org/licenses/lgpl.txt)
