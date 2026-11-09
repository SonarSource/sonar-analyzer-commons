# Common analyzer utilities

Production code is under `src/main/java/org/sonarsource/analyzer/commons/`.
Tests mirror that package under `src/test/java/`. Start with `TokenLocation`
and `TokenLocationTest` for a small utility and its output assertions.

`RuleMetadataLoader` combines annotations with JSON/HTML rule resources.
`BuiltInQualityProfileJsonLoader` loads profile definitions. Their test resources
contain deliberately invalid and historical data. Change a resource only when
the loader behavior under test requires it.

`appsec/` contains IP, protocol, key-size, test-file, and secret classification.
Secret configuration exports consume `SecretClassifier` from the configurations
module. A classifier change therefore requires both module suites and a package
verification. `collections/` contains persistent collection helpers. Preserve
their immutability and iterator contracts.

The POM relocates `org.json.simple` into an internal namespace during shading.
Do not expose that shaded package in a public API. `verify` checks the existing
JAR size limit. New dependencies increase the size of many analyzers.

From the root, run `mvn -pl commons -am test`. A change to a public
utility also needs a check of affected analyzer callers outside this repository.
