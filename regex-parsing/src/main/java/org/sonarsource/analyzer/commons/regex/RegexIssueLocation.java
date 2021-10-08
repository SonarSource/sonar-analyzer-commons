package org.sonarsource.analyzer.commons.regex;

import java.util.Collections;
import java.util.List;
import org.sonarsource.analyzer.commons.regex.ast.RegexSyntaxElement;

public class RegexIssueLocation {

  private final List<RegexSyntaxElement> syntaxElements;
  private final String message;

  public RegexIssueLocation(RegexSyntaxElement syntaxElement, String message) {
    this(Collections.singletonList(syntaxElement), message);
  }

  public RegexIssueLocation(List<RegexSyntaxElement> syntaxElements, String message) {
    this.syntaxElements = syntaxElements;
    this.message = message;
  }

  public List<RegexSyntaxElement> syntaxElements() {
    return syntaxElements;
  }

  public String message() {
    return message;
  }
}
