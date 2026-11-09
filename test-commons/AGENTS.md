# Shared issue verifier

`SingleFileVerifier` and `MultiFileVerifier` are the public entry points under
`src/main/java/org/sonarsource/analyzer/commons/checks/verifier/`.
They delegate to `internal/`. `quickfix/` contains edit and quick-fix data.
`CommentParser` reads expected issue comments. See the module README for the
comment notation and the public interfaces for coordinate contracts.

The verifier compares reported issues with expected comments, locations, flows,
and fixes. Changes to its matching or diagnostics affect many analyzers.
Preserve the public API and test both accepted input and useful failure output.
Start with `SingleFileVerifierTest` or `FileContentTest` for the relevant case.

Files under `src/test/resources/` are source samples, including deliberate
errors, whitespace, and expected issue files. Do not format or regenerate them
in bulk. Add the smallest sample that exposes the behavior.

Run `mvn -pl test-commons -am test` from the root. Its reactor
consumers include `xml-parsing`, `test-xml-parsing`, and `regex-parsing`.
