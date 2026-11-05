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
It includes the existing exclusions and the additional identifier inventory in one document:

- `match`: matching semantics for `hostPatterns`, `safeUrlPrefixes` and `identifierUrls`.
- `cleartextProtocols`: recognized cleartext schemes with the `://` suffix.
- `hostPatterns`: the `internal`, `namespace` and `documentation` host regexes.
- `safeUrlPrefixes`: existing identifier URL prefixes that the Java filter treats as safe.
- `identifierUrls`: exact authorities mapped to case-sensitive path + query + fragment regexes.
  These entries require language-specific context checks before suppressing a finding.

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
  },
  "identifierUrls": {
    "authority": {
      "semantics": "exact",
      "caseInsensitive": true
    },
    "pattern": {
      "semantics": "find",
      "caseInsensitive": false,
      "target": "pathQueryFragment"
    }
  }
}
```

`find` means regex search rather than whole-value matching. `prefix` means starts-with matching;
`fullUrl` means the complete URL after stripping surrounding whitespace, retaining any port and user information.
Host patterns and safe URL prefixes are case-insensitive. For `identifierUrls`, `exact` means the complete
authority must equal a map key, case-insensitively, retaining any port and user information. The associated
regex uses case-sensitive `find` semantics. Its `pathQueryFragment` target is the original suffix after the
authority, with `/` inserted when the path is empty, as described under **Additional identifier URLs** below.
Host and identifier regexes pass through `RegexTranslator` when exported.

```js
import exclusions from '@sonarsource/analyzer-commons-configurations/cleartext-protocol-exclusions.json' with { type: 'json' };

const hostPatterns = Object.values(exclusions.hostPatterns).map(regex => new RegExp(regex, 'i'));
const identifierPatterns = new Map(
  Object.entries(exclusions.identifierUrls).map(([authority, regex]) => [authority, new RegExp(regex)]),
);
```

### Existing exclusions

Preserve `CleartextProtocolFilter.isSafeWithoutTls` semantics:

- Strip surrounding whitespace. Check `safeUrlPrefixes` case-insensitively against the complete URL.
  An explicit port or user information changes that prefix and must not be removed before this check.
- For a valid URI with a scheme and host, non-cleartext schemes are safe. For cleartext schemes, apply
  `hostPatterns` case-insensitively with search semantics to the extracted host. These existing host
  exclusions allow ports; they do not use the stricter authority matching of `identifierUrls`.
- The Java filter also accepts single-label hostnames for valid URIs, excluding IPv6 and decimal or
  hexadecimal numeric IP literals. This parsing logic remains the consuming analyzer's responsibility.
- When strict URI parsing fails or yields no host, use the Java filter's lenient cleartext-authority
  extraction (skipping optional user information) and apply only the host patterns. Do not grant the
  single-label-host exception through this fallback. If no cleartext authority can be extracted,
  a string is safe only when it does not start with a recognized cleartext protocol prefix.

### Additional identifier URLs

Follow `CleartextProtocolFilter.isKnownIdentifier` semantics separately:

- Strip surrounding whitespace as Java's `String.strip()` does and accept only `http://`, case-insensitively.
  Reject the URL if ASCII whitespace or a backslash (`\`) remains anywhere in it.
- The authority is everything after `http://` up to the first `/`, `?` or `#`, or the end of the string.
  The remaining text, including that separator if present, is the suffix.
- Look up the complete authority case-insensitively, including any explicit port. Do not remove ports:
  `example.invalid:80` does not match `example.invalid`. Subdomains and user information do not match either.
- Apply the regex case-sensitively with search semantics (`RegExp.test`) to the original path + query + fragment.
  Use `/` for an empty suffix, and prepend `/` when the suffix starts with `?` or `#`.
  Do not normalize the path or decode escapes.

An `identifierUrls` match recognizes an identifier; it does not make an HTTP connection safe.
Each analyzer must check the language-specific context before suppressing a finding.

### Shared regression cases

`cleartext-protocol-corpus.json` is exported from the shared resource
`commons/src/test/resources/org/sonarsource/analyzer/commons/appsec/cleartext-protocol-corpus.json`.
Both the Commons filter tests and the configurations module consume these cases. Update this resource when adding
regression examples; Maven copies it into the configurations module without adding it to the Commons runtime JAR.

- `safeUrls` and `unsafeUrls` are expected to return `true` and `false` respectively from the baseline safety filter.
- `identifierUrls` and `nonIdentifierUrls` are expected to return `true` and `false` respectively from the additional
  identifier matcher. Every positive identifier case must still return `false` from the baseline safety filter.

Run these cases against your consumer to check parity with Java, including the distinction between an identifier
and a request destination. Update the shared resource when changing the inventory.

Maven verification checks the exported configuration and corpus in Java. Consuming analyzers such as SonarJS
must verify npm imports and run the complete shared corpus in their own runtime, where URL parsing may differ.

See [SonarSource/sonar-analyzer-commons](https://github.com/SonarSource/sonar-analyzer-commons) for details.
