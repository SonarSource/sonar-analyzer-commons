# SonarAnalyzer.CommonsConfigurations

Shared analyzer configuration data (secret-exclusion regexes) generated from the sonar-analyzer-commons
SecretClassifier, for use by non-JVM SonarSource analyzers.

The payload is `content/secret-patterns.json`.

`content/secret-exclusion-corpus.json` is the matching validation corpus. Every `knownNonSecrets` value must be
suppressed by those patterns in your regex engine, and no `secretCandidates` value may be - assert both in your own
test suite, so a pattern that fails to compile or behaves differently outside the JVM is caught here rather than
becoming a missed or a noisy secret finding. A sample's `category` is informational and should not be asserted on.

See [SonarSource/sonar-analyzer-commons](https://github.com/SonarSource/sonar-analyzer-commons) for details.
