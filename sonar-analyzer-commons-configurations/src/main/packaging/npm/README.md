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

## Identifier URLs

`identifier-urls.json` is generated from the inventory used by `CleartextProtocolFilter.isKnownIdentifier`.
It maps exact authorities to regex strings; do not edit the generated file by hand.

```js
import identifierUrls from '@sonarsource/analyzer-commons-configurations/identifier-urls.json' with { type: 'json' };

const patterns = new Map(
  Object.entries(identifierUrls).map(([authority, regex]) => [authority, new RegExp(regex)]),
);
```

Consumers must follow the Java matcher's semantics:

- Strip surrounding whitespace and accept only `http://`, case-insensitively. Reject the URL if ASCII whitespace
  or a backslash (`\`) remains anywhere in it.
- The authority is everything after `http://` up to the first `/`, `?` or `#`, or the end of the string.
  The remaining text, including that separator if present, is the suffix.
- Look up the complete authority case-insensitively, including any explicit port. Do not remove ports:
  `example.invalid:80` does not match `example.invalid`. Subdomains and user information do not match either.
- Apply the regex case-sensitively with search semantics (`RegExp.test`) to the original path + query + fragment.
  Use `/` for an empty suffix, and prepend `/` when the suffix starts with `?` or `#`.
  Do not normalize the path or decode escapes.

A match recognizes an identifier; it does not make an HTTP connection safe. Each analyzer must check the
language-specific context before suppressing a finding.

See [SonarSource/sonar-analyzer-commons](https://github.com/SonarSource/sonar-analyzer-commons) for details.
