# Regex parsing and shared checks

`RegexSource` selects a character parser, lexer, and supported features.
Adapters live in `java/`, `php/`, and `python/`. `RegexParser.parse()` builds
the AST, automaton entry/exit states, and syntax-error list in `RegexParseResult`.
`ast/` holds syntax nodes and automaton states. `helpers/` contains reachability,
character-class, and automata operations. `finders/` contains shared checks.

Keep syntax parsing separate from check logic. A feature change needs tests for
each applicable dialect and flag. Check unsupported and malformed forms too.
Index ranges map back to source text; preserve their offset and escaping rules.
Use the adjacent node/parser test for syntax changes.

Finder tests pair a Java `*FinderTest` with
`src/test/resources/finders/<Finder>.yml`. `Verifier` loads the YAML;
`RegexFinderVerifier` and `FinderCheck` connect parsed expressions to the common
issue verifier. `EmptyGroupFinderTest` is a small reference example. Preserve
expected locations and compliant cases when changing a finding.

From the root, run `python3 scripts/dev.py test regex-parsing --test
EmptyGroupFinderTest`, then `python3 scripts/dev.py test regex-parsing`.
This module uses `test-commons` in test scope. Maven's reactor builds that
dependency with `-am`. No SonarQube server or external regex engine is needed.
