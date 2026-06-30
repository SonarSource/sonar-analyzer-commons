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

public final class SecretClassifier {


  private SecretClassifier() {
  }

  /**
   * Returns {@code true} when the value matches a known skip pattern, such as a fake value, a variable reference, or
   * an encrypted placeholder.
   *
   * @param candidate the string to classify
   * @param context surrounding information; pass {@link Context#empty()} when none is available
   * @return {@code true} if the value is recognized as a non-secret
   */
  @SuppressWarnings("java:S1172")
  public static boolean isKnownNonSecret(String candidate, Context context) {
    return false;
    }

  /**
   * Convenience overload that classifies a value with an empty {@link Context}.
   *
   * @param candidate the string to classify
   * @return {@code true} if the value is recognized as a non-secret
   */
  public static boolean isKnownNonSecret(String candidate) {
    return isKnownNonSecret(candidate, Context.empty());
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
      return EmptyContext.INSTANCE;
    }
  }

  private enum EmptyContext implements Context {
    INSTANCE
  }
}
