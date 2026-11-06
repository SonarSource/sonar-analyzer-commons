# Code recognition

`CodeRecognizer` combines the weighted `Detector` results from a
`LanguageFootprint`. A language analyzer supplies the footprint. This module
does not parse the language or report Sonar issues.

Start with `CodeRecognizerTest` and a detector's adjacent test. Test code-like
and ordinary text, threshold boundaries, and empty inputs for relevant changes.
Keep language-specific footprints outside this shared module.

Run `python3 scripts/dev.py test recognizers` from the root. Tests use JUnit 4.
`verify` also enforces the existing 10–11 KB JAR size range in the module POM.
