# @sonarsource/analyzer-commons-configurations

Shared analyzer configuration data generated from sonar-analyzer-commons for non-JVM SonarSource analyzers.

## Secret exclusions

The secret-exclusion patterns generated from SecretClassifier are in `secret-patterns.json`.

`secret-exclusion-corpus.json` is the matching validation corpus. Every `knownNonSecrets` value must be suppressed by
`secret-patterns.json` in your regex engine - either matched by a `patternGroups` regex or equal to an
`exactMatchGroups` value, following the `match` semantics declared in that file - and no `secretCandidates` value may
be. Assert both in your own test suite, so a pattern that fails to compile or behaves differently outside the JVM is
caught here rather than becoming a missed or a noisy secret finding. A sample's `category` is informational and should
not be asserted on.

## Cleartext-protocol exclusions

`cleartext-protocol-exclusions.json` is generated directly from `CleartextProtocolFilter`.
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
`fullUrl` means the complete URL after stripping surrounding whitespace, retaining any port and user information.
Both matching operations are case-insensitive.

```js
import exclusions from '@sonarsource/analyzer-commons-configurations/cleartext-protocol-exclusions.json' with { type: 'json' };

const hostPatterns = Object.values(exclusions.hostPatterns).map(regex => new RegExp(regex, 'i'));
```

### Existing exclusions

Preserve `CleartextProtocolFilter.isSafeWithoutTls` semantics:

- Strip surrounding whitespace. Check `safeUrlPrefixes` case-insensitively against the complete URL.
  An explicit port or user information changes that prefix and must not be removed before this check.
- For a valid URI with a scheme and host, non-cleartext schemes are safe. For cleartext schemes, apply
  `hostPatterns` case-insensitively with search semantics to the extracted host. These existing host
  exclusions allow ports.
- The Java filter also accepts single-label hostnames for valid URIs, excluding IPv6 and decimal or
  hexadecimal numeric IP literals. This parsing logic remains the consuming analyzer's responsibility.
- When strict URI parsing fails or yields no host, use the Java filter's lenient cleartext-authority
  extraction (skipping optional user information) and apply only the host patterns. Do not grant the
  single-label-host exception through this fallback. If no cleartext authority can be extracted,
  a string is safe only when it does not start with a recognized cleartext protocol prefix.

### Shared regression cases

`cleartext-protocol-corpus.json` is generated from the sample lists in `CleartextProtocolCorpusExporter`.
The configurations module's tests validate these same lists against the Java filter:

- `safeUrls` and `unsafeUrls` are expected to return `true` and `false` respectively from the baseline safety filter.

Run these cases against your consumer to check parity with the Java safety filter.

The Maven `verify` phase unpacks the assembled npm tarball and checks the documented Node.js imports against
the Java-generated JSON. This requires Node.js (provided by the repository's `mise.toml`) and `tar`.

See [SonarSource/sonar-analyzer-commons](https://github.com/SonarSource/sonar-analyzer-commons) for details.
