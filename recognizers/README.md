SonarSource Analyzers Recognizers
=========================
Classes required to detect code-like strings (e.g. for commented out code)

`CodeRecognizer` combines the weighted detectors supplied by a
`LanguageFootprint`. `KeywordsDetector`, `EndWithDetector`, `ContainsDetector`,
`CamelCaseDetector`, and `RegexDetector` supply individual recognition signals.
Start with `CodeRecognizerTest` for a small test example. Run this module with
`mvn -pl recognizers -am test` from the repository root.

### License
Copyright 2009-2023 SonarSource.
Licensed under the [GNU Lesser General Public License, Version 3.0](http://www.gnu.org/licenses/lgpl.txt)
