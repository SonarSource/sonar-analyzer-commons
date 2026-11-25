# sonar-analyzer-commons-configurations

Shared analyzer configuration data generated from sonar-analyzer-commons for non-JVM SonarSource analyzers.

## Installation

Declare the SonarSource Cargo registry in `.cargo/config.toml`. RepoX requires authentication, so
Cargo also needs a credential provider:

```toml
[registries.sonarsource]
index = "sparse+https://repox.jfrog.io/artifactory/api/cargo/sonarsource-cargo-public-releases/index/"
credential-provider = "cargo:token"
```

Supply a RepoX reader token through the environment, for example in CI:

```shell
export CARGO_REGISTRIES_SONARSOURCE_TOKEN="Bearer <RepoX access token>"
```

Then add the dependency to `Cargo.toml`. Versions are SemVer pre-releases (`X.Y.Z-BUILD`); pin
one exactly, so that the patterns only change with an explicit version bump:

```toml
sonar-analyzer-commons-configurations = { version = "=2.38.0-5300", registry = "sonarsource" }
```

The crate exports four JSON strings: `SECRET_PATTERNS_JSON`, `SECRET_EXCLUSION_CORPUS_JSON`,
`CLEARTEXT_PROTOCOL_EXCLUSIONS_JSON`, and `CLEARTEXT_PROTOCOL_CORPUS_JSON`. It supplies data only;
the consuming analyzer parses the JSON and implements the matching semantics.

## Secret exclusions

`SECRET_PATTERNS_JSON` contains the secret-exclusion patterns generated from `SecretClassifier`.

`SECRET_EXCLUSION_CORPUS_JSON` contains the matching validation corpus. Every `knownNonSecrets`
value must be suppressed by `SECRET_PATTERNS_JSON` in your regex engine - either matched by a
`patternGroups` regex or equal to an `exactMatchGroups` value, following the `match` semantics
declared in that JSON - and no `secretCandidates` value may be. Assert both in your own test suite,
so a pattern that fails to compile or behaves differently outside the JVM is caught here rather
than becoming a missed or a noisy secret finding. A sample's `category` is informational and should
not be asserted on.

One pattern uses a backreference, `(?<repeated>.)\k<repeated>{3}`, which the `regex` crate does not
support. Use `fancy-regex` to compile and apply the exported patterns.

## Cleartext-protocol exclusions

`CLEARTEXT_PROTOCOL_EXCLUSIONS_JSON` is generated directly from `CleartextProtocolFilter`.
It includes the existing exclusions in one document:

- `match`: matching semantics for `hostPatterns` and `safeUrlPrefixes`.
- `cleartextProtocols`: recognized cleartext schemes with the `://` suffix.
- `hostPatterns`: the `internal`, `namespace` and `documentation` host regexes.
- `safeUrlPrefixes`: existing identifier URL prefixes that the Java filter treats as safe.

Do not maintain a copy of these lists in a consumer or edit the generated JSON by hand.

The `match` metadata declares how consumers must apply the exclusions:

```json
{
  "hostPatterns": {
    "semantics": "find",
    "caseInsensitive": true
  },
  "safeUrlPrefixes": {
    "semantics": "prefix",
    "caseInsensitive": true,
    "target": "fullUrl"
  }
}
```

`find` means regex search rather than whole-value matching. `prefix` means starts-with matching;
`fullUrl` means the complete URL after stripping surrounding whitespace, retaining any port and
user information. Both matching operations are case-insensitive.

```rust
let exclusions: serde_json::Value = serde_json::from_str(
    sonar_analyzer_commons_configurations::CLEARTEXT_PROTOCOL_EXCLUSIONS_JSON,
)?;
```

### Existing exclusions

Preserve `CleartextProtocolFilter.isSafeWithoutTls` semantics:

- Strip surrounding whitespace. Check `safeUrlPrefixes` case-insensitively against the complete URL.
  An explicit port or user information changes that prefix and must not be removed before this check.
- For a valid URI with a scheme and host, non-cleartext schemes are safe. For cleartext schemes,
  apply `hostPatterns` case-insensitively with search semantics to the extracted host. These
  existing host exclusions allow ports.
- The Java filter also accepts single-label hostnames for valid URIs, excluding IPv6 and decimal
  or hexadecimal numeric IP literals. This parsing logic remains the consuming analyzer's
  responsibility.
- When strict URI parsing fails or yields no host, use the Java filter's lenient
  cleartext-authority extraction (skipping optional user information) and apply only the host
  patterns. Do not grant the single-label-host exception through this fallback. If no cleartext
  authority can be extracted, a string is safe only when it does not start with a recognized
  cleartext protocol prefix.

### Shared regression cases

`CLEARTEXT_PROTOCOL_CORPUS_JSON` is exported from the shared resource
`commons/src/test/resources/org/sonarsource/analyzer/commons/appsec/cleartext-protocol-corpus.json`.
Both the Commons filter tests and the configurations module consume these cases. Update this
resource when adding regression examples; Maven copies it into the configurations module without
adding it to the Commons runtime JAR.

- `safeUrls` and `unsafeUrls` are expected to return `true` and `false` respectively from the
  baseline safety filter.

Run these cases against your consumer to check parity with the Java safety filter.

Maven verification checks the exported configuration and corpus in Java. Consuming analyzers must
verify the crate constants and run the complete shared corpus in their own runtime, where URL
parsing may differ.

See [SonarSource/sonar-analyzer-commons](https://github.com/SonarSource/sonar-analyzer-commons)
for details.
