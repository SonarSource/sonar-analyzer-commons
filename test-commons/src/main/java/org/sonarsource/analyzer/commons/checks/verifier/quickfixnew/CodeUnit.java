package org.sonarsource.analyzer.commons.checks.verifier.quickfixnew;

import java.util.List;
import java.util.stream.Collectors;
import org.sonarsource.analyzer.commons.checks.verifier.quickfix.TextEdit;
import org.sonarsource.analyzer.commons.checks.verifier.quickfix.TextSpan;

public final class CodeUnit {
  private final List<String> lines;

  public static CodeUnit from(String code) {
    return new CodeUnit(code.lines().collect(Collectors.toList()));
  }

  private CodeUnit(List<String> lines) {
    this.lines = lines;
  }

  public void apply(TextEdit edit) {
    var zeroBasedStartLine = edit.getTextSpan().startLine - 1;
    var zeroBasedEndLine = edit.getTextSpan().endLine - 1;
    var zeroBasedStartCol = edit.getTextSpan().startCharacter - 1;
    var zeroBasedEndCol = edit.getTextSpan().endCharacter - 1;
    var linesIter = lines.listIterator(zeroBasedStartLine);
    var firstLine = linesIter.next();
    linesIter.remove();
    var lastLine = firstLine;
    var endIdx = zeroBasedEndLine - 1;
    while (linesIter.nextIndex() <= endIdx) {
      lastLine = linesIter.next();
      linesIter.remove();
      endIdx -= 1;
    }
    var editIter = edit.getReplacement().lines().iterator();
    var isFirst = true;
    while (editIter.hasNext()) {
      var currNewLine = "";
      if (isFirst) {
        currNewLine += firstLine.substring(0, zeroBasedStartCol);
        isFirst = false;
      }
      currNewLine += editIter.next();
      if (!editIter.hasNext()) {
        currNewLine += lastLine.substring(zeroBasedEndCol);
      }
      linesIter.add(currNewLine);
    }
  }

  @Override
  public String toString() {
    return String.join("\n", lines);
  }

}
