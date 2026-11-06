# Shared configuration export

`SecretPatternsExporter` reads the `commons` module's `SecretClassifier` views.
`RegexTranslator` converts the patterns for non-JVM consumers.
`SecretExclusionCorpusExporter` exports the sample corpus. `JsonExports` writes
the JSON. Keep the classifier as the source of truth.

The Maven `prepare-package` phase runs both exporters after compilation.
It also filters the source manifests under `src/main/packaging/` into
`target/packaging/`. The `package` phase assembles npm and NuGet archives and
attaches the raw JSON to the Maven build. These are data packages. They have no
npm or NuGet dependency install, so a package-manager lockfile is not needed.

Do not edit generated JSON or archives under `target/`. Source templates and
assembly descriptors are editable, but changes affect external consumers.
Preserve the npm `package/` layout, NuGet metadata, and version conversion.
CI handles signing, deployment, and promotion; local packaging does not need
those credentials.

Run `python3 scripts/dev.py test sonar-analyzer-commons-configurations`, then
`python3 scripts/dev.py verify sonar-analyzer-commons-configurations` from the
root. Inspect exporter tests for exact JSON and regex behavior. Archive outputs
are under this module's `target/`; tests alone do not run the packaging phase.
