/*
 * SonarSource Analyzers Commons
 * Copyright (C) SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * You can redistribute and/or modify this program under the terms of
 * the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonarsource.analyzer.commons.appsec;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Classifies string values against a shared set of "skip" patterns: values that look like fake credentials,
 * placeholders, variable references, or encrypted markers and should therefore not be reported as hardcoded secrets.
 *
 * <p>Classification takes the candidate value plus a {@link Context}. The context is an extensible carrier for future extensions.
 */
public final class SecretClassifier {

  /**
   * Coarse group a skip pattern belongs to, and the key the corpus samples are declared under; see
   * {@link #exportKnownNonSecretSamples(Category)}.
   */
  public enum Category {
    /** Trivially fake or weak literals: fake words, password-like values, repeated, too-short, or masked strings. */
    FAKE_VALUE,
    /** Well-known literal placeholder secrets matched in full, e.g. {@code hunter2}, {@code letmein}. */
    SECRET,
    /** Templating, interpolation, variable references and env/config lookups where the value comes from elsewhere. */
    PLACEHOLDER,
    /** Encrypted markers wrapping a ciphertext, e.g. {@code {cipher}...}, {@code enc[...]}. */
    ENCRYPTED,
    /** Pointers into an external secret store rather than a literal, e.g. an AWS Secrets Manager ARN, {@code op://}. */
    REFERENCE,
    /** Recognizable structured values that are not credentials, e.g. filesystem paths. */
    STRUCTURED_FORMAT
  }

  /**
   The skip patterns of a single {@link Category}, grouped to keep the configuration readable.
   */
  @SuppressWarnings("java:S1068")
  private static final class PatternGroup {
    private final Category category;
    private final List<Pattern> patterns;

    PatternGroup(Category category, List<Pattern> patterns) {
      this.category = category;
      this.patterns = patterns;
    }

    static PatternGroup of(Category category, String... regexes) {
      return new PatternGroup(category, Arrays.stream(regexes).map(SecretClassifier::compile).toList());
    }

    List<Pattern> patterns() {
      return patterns;
    }
  }

  /** Values of a single {@link Category} matched in full, case-insensitively, via a set rather than a regex. */
  @SuppressWarnings("java:S1068")
  private static final class ExactMatchGroup {
    private final Category category;
    private final Set<String> values;

    ExactMatchGroup(Category category, Set<String> values) {
      this.category = category;
      this.values = values;
    }

    Set<String> values() {
      return values;
    }
  }

  private static final List<PatternGroup> PATTERN_GROUPS = List.of(

    // Trivially fake or weak literal values.
    PatternGroup.of(Category.FAKE_VALUE,
      // Expect minimum length of 6 characters
      "^.{0,5}$",
      // Words usually found in fake secrets, e.g. "samplepassword", "EXAMPLE_SECRET"
      "sample|example|placeholder|replace|change|foo|bar|test|fake|abcd",
      "redacted|cafebabe|deadbeef|whatever|123456|admin|pass|secret|default|dummy|qwerty|setting|obfuscated",
      // Password-like words, e.g. "password", "passwd", "pass", "password1234"
      "^(my)?pass(word|wd)?\\d{0,5}+$",
      // Leetspeak "password" variants the "pass" substring misses, e.g. "p@ssword", "p@ssw0rd"
      "p[@a]ssw[o0]rd",
      // Boolean / null / scalar literals, e.g. "password = undefined", "enabled: true"
      "^(?:none|undefined|null|true|false|yes|no|1|0)$",
      // Starts with "your", e.g. "yourpassword", "your_super_secret"
      "^your",
      // Same character 4 times in a row, e.g. "abbbbc"
      "(?<repeated>.)\\k<repeated>{3}",
      // A secret being masked or shortened, e.g. "1fj28...askn3i"
      "\\.\\.\\."),

    // Templating, interpolation and env/config lookups where the value comes from elsewhere.
    PatternGroup.of(Category.PLACEHOLDER,
      // Variable interpolation, e.g. "${secret}", "$${camel}", starting with the interpolation
      "^(?:\\\\)?\\${1,2}\\{[^}]++\\}",
      // Variable interpolation ending with the interpolation
      "(?:\\\\)?\\${1,2}\\{[^}]++\\}$",
      // Variable interpolation, e.g. "#{{secret}}", "##(password)"
      "^\\#{1,2}[{(]",
      // Concourse ((vars))
      "^\\(\\(.*\\)\\)$",
      // Shell command substitution, e.g. "$(echo $PASSWORD)"
      "^\\$\\(",
      // Shell command substitution, e.g. "`echo $PASSWORD`"
      "^`[^`]++`$",
      // Variable references, e.g. "$a", "$foo_bar", "$$R", "$password$"
      "^(?:\\\\)?\\${1,2}\\w+\\${0,2}$",
      // Variable interpolation in templates, e.g. "{secret}", "%{secret}"
      "^%?\\{[^}]++\\}$",
      // Variable interpolation in templates, e.g. "{{secret}}", "{{{password}}}"
      "^\\{{2,}[^}]++\\}{2,}",
      // Environment variable access, e.g. "System.getenv(\"secret\")", "ENV['SECRET']"
      "\\b(get)?env(iron)?\\b",
      // Node.js environment variable access, e.g. "process.env.MY_SECRET"
      "process\\.env\\.",
      // Environment variables with %...% syntax, e.g. "%GITHUB_TOKEN%"
      "^%[^%]++%$",
      // Configuration access, e.g. "config['secret']", "config('password')"
      "config[\\(\\[]",
      // PowerShell cmdlet to read console input
      "Read-Host",
      // Angle-bracketed placeholders, e.g. "<password>"
      "^<[\\w\\.\\t -]{1,10}>",
      "^<[^>]++>$",
      // Normal (potentially escaped) bracketed placeholders, e.g. "(password)", jq "(.password)"
      "^\\\\?\\([^)]++\\)$",
      // Square-bracketed placeholders, e.g. "[password]"
      "^\\[[^\\]]++\\]$",
      // Python format string placeholders, e.g. "%(password)s"
      "^%\\([^)]++\\)s$",
      // Azure Logic Apps runtime expressions, e.g. "@variables('name')", "@body('action')"
      "^@\\w++\\([^)]*+\\)$",
      // Double-underscore-wrapped placeholders, e.g. "__some_placeholder_password__"
      "^__.+__$",
      // Code-reminder prefix left as the full credential value, e.g. "(prefix): replace with real key"
      "^(?:todo|fixme)\\b"),

    // Encrypted markers wrapping a ciphertext.
    PatternGroup.of(Category.ENCRYPTED,
      // Encrypted secrets, encrypted:<base64>
      "^encrypted:[a-zA-Z0-9+\\/]++={0,2}$",
      // Encrypted spring cloud secrets, e.g. "{cipher}1e3faa2cdab2deae117dca102e52922a"
      "^\\{cipher\\}",
      // Encrypted string literals, e.g. "enc[...]", "enc{...}", "%enc{...}", "ENC(...)"
      "^enc\\[",
      "^%?enc\\{",
      "^enc\\([^)]*+\\)$"),

    // Pointers into an external secret store rather than a literal value.
    PatternGroup.of(Category.REFERENCE,
      // ARN to an AWS Secrets Manager secret
      "^arn:aws:secretsmanager:",
      // 1Password URLs, e.g. "op://vault/secret"
      "^op:\\/[\\S\\ ]++$",
      // HashiCorp/Cirrus Vault references, e.g. "VAULT[path/to/secret access_token]"
      "^vault\\["),

    // Recognizable structured values that are not credentials.
    PatternGroup.of(Category.STRUCTURED_FORMAT,
      // Filesystem paths with at least 3 segments, e.g. "/path/to/file.ext"
      "^(?:/[a-z0-9_.-]++){3,}+$",
      // Semantic version strings, optionally prefixed, e.g. "v1.2.3", ">=1.0.0", "~1.4.5-alpha" (semver.org regex)
      "^(?:>=?|<=?|[~^])?v?(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"
        + "(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?"
        + "(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$",
      // Resolved / peer-annotated version strings from package lockfiles, e.g. "4.0.9(@types/node@22.13.4)".
      // Stopgap: such values may instead be excluded by ignoring lockfiles by path.
      "^v?\\d++(?:\\.\\d++)++(?:\\([^()]*+\\))++$"));

  // Flattened once: isKnownNonSecret is on every check's hot path, so avoid re-flattening PATTERN_GROUPS per call.
  private static final List<Pattern> ALL_PATTERNS = PATTERN_GROUPS.stream()
    .flatMap(group -> group.patterns().stream())
    .toList();

  // Well-known placeholder secrets plus config/credential vocabulary, matched in full (case-insensitive).
  private static final ExactMatchGroup SECRET_VALUES = new ExactMatchGroup(Category.SECRET, Set.of(
    "hunter2", "letmein", "abc123",
    "changeme", "changeit", "unknown", "optional", "enabled", "disabled",
    "string", "random", "token"));

  /**
   * One representative sample per skip pattern and per exact-match value, keyed by the {@link Category} that must
   * suppress it. Each sample is suppressed by the category it is listed under, not by an earlier group; adding a
   * pattern without adding a sample here fails the classifier's coverage test.
   *
   * <p>Declared in main scope rather than in the test so the build can publish it as a validation corpus, letting
   * non-JVM analyzers assert their own regex engine reproduces this behavior instead of hand-copying the samples.
   */
  private static final Map<Category, List<String>> KNOWN_NON_SECRET_SAMPLES = Collections.unmodifiableMap(new EnumMap<>(Map.of(

    Category.FAKE_VALUE, List.of(
      // Minimum length
      "", "abc",
      // Fake-word substrings
      "samplepassword", "EXAMPLE_SECRET", "deadbeef", "qwerty",
      // Templates whose placeholder names contain credential words - FAKE_VALUE wins before PLACEHOLDER
      "${secret}", "#{{secret}}", "$foo_bar",
      // Password-like values
      "password1234", "passwd",
      // Leetspeak "password" variants with "@" that the "pass" substring misses
      "p@ssword", "p@ssw0rd",
      // Boolean / null / scalar literals
      "undefined", "true", "null",
      // "your..." prefix
      "yourpassword",
      // Same-character repetitions
      "abbbbc", "111111",
      // Masked value
      "1fj28...askn3i",
      // Other fake keywords
      "admin123", "vncpass", "super-secret-p4ssw0rd",
      // "secret" in "secretsmanager" triggers FAKE_VALUE before REFERENCE; kept here for REFERENCE ARN pattern coverage
      "arn:aws:secretsmanager:us-east-1:123456789012:secret:db-pass"),

    Category.SECRET, List.of(
      "hunter2", "letmein", "abc123",
      "changeme", "changeit", "unknown", "optional", "enabled", "disabled", "string", "random", "token"),

    Category.PLACEHOLDER, List.of(
      // double-underscore-wrapped
      "__api_key__",
      // code-reminder prefix
      "TODO: fill in", "FIXME: fill in",
      // variable interpolation
      "${env_var}", "value-${env_var}",
      // hash-brace interpolation
      "#{{db_host}}",
      // Concourse vars
      "((vault_ref))",
      // shell command substitution
      "$(get_key)",
      // backtick command substitution
      "`get_key`",
      // bare variable reference
      "$MY_VAR",
      // template interpolation
      "{db_host}", "%{db_host}",
      // double-brace interpolation
      "{{db_host}}",
      // env access
      "System.getenv(\"DB_HOST\")",
      // Node.js process.env
      "process.env.HOST",
      // %VAR% syntax
      "%GITHUB_TOKEN%",
      // config access
      "config['db_url']",
      // PowerShell
      "Read-Host",
      // short angle-bracket placeholder
      "<db-host>",
      // long angle-bracket placeholder
      "<api_endpoint>",
      // parenthesised placeholder
      "(config_ref)",
      // square-bracket placeholder
      "[db_url]",
      // Python format-string placeholder
      "%(db_url)s",
      // Azure Logic Apps expression
      "@variables('host')"),

    Category.ENCRYPTED, List.of(
      "encrypted:YWJjZGVm",
      "{cipher}1e3faa2cdab2deae117dca102e52922a",
      "enc[QUJDRA==]",
      "ENC{QUJDRA==}", "%enc{QUJDRA==}", "ENC(QUJDRA==)"),

    Category.REFERENCE, List.of(
      "op://vault/item/key",
      "VAULT[path/to/key access_token]"),

    Category.STRUCTURED_FORMAT, List.of(
      "/var/keys/gsa-key.json",
      // semver variants
      "v1.2.3", ">=1.0.0", "~1.4.5-alpha",
      // peer-annotated lockfile version (non-semver)
      "4.0.9(@types/node@22.13.4)"))));

  /**
   * Values that must NOT be classified as known non-secrets: realistic credentials plus near-misses of the skip
   * patterns above. Published alongside {@link #KNOWN_NON_SECRET_SAMPLES} because a pattern that is too broad in a
   * foreign regex engine silently hides real hardcoded secrets, which the positive samples alone cannot detect.
   */
  private static final List<String> SECRET_CANDIDATE_SAMPLES = List.of(
    "Xk9Lm2Qp7Rs4Tv1Wz0",
    "9f8e7d6c5b4a392817",
    "Tr0ub4dor&3xpl0!t",
    // Leading "__" without a closing "__"
    "__not_closed",
    // Credential words are matched only as whole values, so a value that merely contains one stays a candidate
    "mytoken123",
    "this_should_remain_unknown");

  // Context is an empty extension point today, so the analyzer sees instantiating it as pointless; the single shared
  // empty instance is intentional and lets empty() return a non-null context.
  @SuppressWarnings("java:S2440")
  private static final Context EMPTY_CONTEXT = new Context() {
  };

  private SecretClassifier() {
  }

  private static Pattern compile(String regex) {
    return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
  }

  /**
   * Returns {@code true} when the value matches a known skip pattern, such as a fake value, a variable reference, or
   * an encrypted placeholder.
   *
   * @param candidate the string to classify, or {@code null}
   * @param context surrounding information; pass {@link Context#empty()} when none is available
   * @return {@code true} if the value is recognized as a non-secret; {@code false} for {@code null}
   */
  // context is unused today; it is the extension point that lets classification become context-aware later.
  @SuppressWarnings("java:S1172")
  public static boolean isKnownNonSecret(@Nullable String candidate, Context context) {
    if (candidate == null) {
      return false;
    }
    if (SECRET_VALUES.values().contains(candidate.toLowerCase(Locale.ROOT))) {
      return true;
    }
    for (Pattern pattern : ALL_PATTERNS) {
      if (pattern.matcher(candidate).find()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Convenience overload that classifies a value with an empty {@link Context}.
   *
   * @param candidate the string to classify, or {@code null}
   * @return {@code true} if the value is recognized as a non-secret; {@code false} for {@code null}
   */
  public static boolean isKnownNonSecret(@Nullable String candidate) {
    return isKnownNonSecret(candidate, Context.empty());
  }

  /** Visible for testing: every configured skip pattern, so a coverage test can assert each one is exercised. */
  static List<Pattern> allPatterns() {
    return ALL_PATTERNS;
  }

  /** Visible for testing: the exact-match values. */
  static Set<String> exactMatchValues() {
    return SECRET_VALUES.values();
  }

  /** Visible for testing: returns the {@link Category} that suppressed the candidate, or {@code null} when not a known non-secret. */
  @Nullable
  static Category classify(@Nullable String candidate) {
    if (candidate == null) {
      return null;
    }
    if (SECRET_VALUES.values().contains(candidate.toLowerCase(Locale.ROOT))) {
      return Category.SECRET;
    }
    for (PatternGroup group : PATTERN_GROUPS) {
      for (Pattern pattern : group.patterns()) {
        if (pattern.matcher(candidate).find()) {
          return group.category;
        }
      }
    }
    return null;
  }

  /**
   * Returns the skip-pattern groups as raw regex source strings, grouped by {@link Category}, in declaration order.
   *
   * <p>Exposed so the build can emit a single machine-readable export (JSON) of the very patterns the JVM classifier
   * uses, keeping non-JVM analyzers (SonarJS, sonar-dotnet, …) in sync without duplicating the list. The patterns are
   * applied case-insensitively with "find" (search-anywhere) semantics, matching {@link #isKnownNonSecret(String, Context)}.
   *
   * @return an immutable, ordered list of pattern groups
   */
  public static List<PatternGroupView> exportPatternGroups() {
    return PATTERN_GROUPS.stream()
      .map(group -> new PatternGroupView(
        group.category.name(),
        group.patterns().stream().map(Pattern::pattern).toList()))
      .toList();
  }

  /**
   * Returns the exact-match value groups, matched in full and case-insensitively, grouped by {@link Category}.
   * Values are sorted so the export is deterministic.
   *
   * @return an immutable, ordered list of exact-match groups
   */
  public static List<ExactMatchGroupView> exportExactMatchGroups() {
    return List.of(new ExactMatchGroupView(
      SECRET_VALUES.category.name(),
      SECRET_VALUES.values().stream().sorted().toList()));
  }

  /**
   * Returns the representative samples that must be classified as known non-secrets by the given {@link Category}, in
   * declaration order. Every skip pattern and every exact-match value is exercised by at least one sample; the
   * classifier's own tests fail if that stops being true.
   *
   * <p>Exposed so the build can publish a validation corpus next to the pattern export, letting non-JVM analyzers
   * (SonarJS, sonar-dotnet, …) assert their regex engine reproduces the JVM behavior rather than hand-copying samples.
   *
   * <p>The category a sample is declared under is informational - it records which group suppresses the value in this
   * implementation, which is first-match-wins and therefore not a stable contract. Consumers should not assert on it.
   *
   * @param category the category whose samples to return
   * @return an immutable, ordered list of samples, empty when the category declares none
   */
  public static List<String> exportKnownNonSecretSamples(Category category) {
    return KNOWN_NON_SECRET_SAMPLES.getOrDefault(category, List.of());
  }

  /**
   * Returns values that must NOT be classified as known non-secrets: realistic credentials and near-misses of the skip
   * patterns. Published with {@link #exportKnownNonSecretSamples(Category)} so a consumer can also detect a pattern
   * that is over-broad in its own regex engine, which would silently suppress real hardcoded secrets.
   *
   * @return an immutable, ordered list of values that stay secret candidates
   */
  public static List<String> exportSecretCandidateSamples() {
    return SECRET_CANDIDATE_SAMPLES;
  }

  /**
   * A group of skip regexes sharing a {@link Category}, exposed for machine-readable export.
   * The regexes are the raw Java source patterns; callers that target other engines are responsible for any
   * translation (e.g. possessive quantifiers to atomic groups for .NET).
   */
  public static final class PatternGroupView {
    private final String category;
    private final List<String> regexes;

    private PatternGroupView(String category, List<String> regexes) {
      this.category = category;
      this.regexes = regexes;
    }

    /** The {@link Category} name this group belongs to. */
    public String category() {
      return category;
    }

    /** The raw regex source strings, in declaration order. */
    public List<String> regexes() {
      return regexes;
    }
  }

  /** A group of exact-match values sharing a {@link Category}, exposed for machine-readable export. */
  public static final class ExactMatchGroupView {
    private final String category;
    private final List<String> values;

    private ExactMatchGroupView(String category, List<String> values) {
      this.category = category;
      this.values = values;
    }

    /** The {@link Category} name this group belongs to. */
    public String category() {
      return category;
    }

    /** The exact-match values, sorted, matched in full and case-insensitively. */
    public List<String> values() {
      return values;
    }
  }

  /**
   * Surrounding information passed alongside the candidate value to {@link #isKnownNonSecret(String, Context)}.
   *
   * <p>The interface is intentionally empty for now. It is a stable extension point: future accessors (for example the
   * key a value was found under, or the analyzed language) can be added without changing the classification signature.
   */
  public interface Context {

    /**
     * Returns a context that carries no additional information.
     *
     * @return the shared empty context
     */
    static Context empty() {
      return EMPTY_CONTEXT;
    }
  }
}
