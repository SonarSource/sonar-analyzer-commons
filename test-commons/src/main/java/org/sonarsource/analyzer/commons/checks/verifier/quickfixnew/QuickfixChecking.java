package org.sonarsource.analyzer.commons.checks.verifier.quickfixnew;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.sonarsource.analyzer.commons.checks.verifier.quickfix.TextEdit;
import org.sonarsource.analyzer.commons.checks.verifier.quickfix.TextSpan;

public final class QuickfixChecking {

//  private QuickfixChecking(){
//  }

  // Assumption: all quickfixes must have disjoint ranges

  public void assertEditsResult(String originalCode, List<TextEdit> edits, String expectedResult){
    var actualResult = applyEdits(originalCode, edits);
    if (!actualResult.equals(expectedResult)) {
      throw new AssertionError(String.format("Expected: %n%s%nbut was: %n%s", expectedResult, actualResult));
    }
  }

  private static String applyEdits(String originalCode, List<TextEdit> edits) {
    edits = edits.stream().sorted(new EditsComparator().reversed()).collect(Collectors.toList());
    var codeUnit = CodeUnit.from(originalCode);
    for (var edit : edits) {
      codeUnit.apply(edit);
    }
    return codeUnit.toString();
  }

  private static final class EditsComparator implements Comparator<TextEdit> {
    @Override
    public int compare(TextEdit e1, TextEdit e2) {
      var major = e1.getTextSpan().startLine - e2.getTextSpan().startLine;
      return major != 0 ? major :
        e1.getTextSpan().startCharacter - e2.getTextSpan().startCharacter;
    }
  }

  @Test
  public void test(){
    var code =
      "class Foo {\n"
        + "  void foo() {\n"
        + "    var l = 0;\n"
        + "    var k = 1;\n"
        + "    System.out.println(l + k);\n"
        + "  }\n"
        + "}";
    var replacement =
      "var sum = 0 + 1;\n"
        + "    System.out.println(sum);";
    var span = new TextSpan(3, 5, 5, 31);
    var edit = TextEdit.replaceTextSpan(span, replacement);
    var expectedResult =
      "class Foo {\n"
        + "  void foo() {\n"
        + "    var sum = 0 + 1;\n"
        + "    System.out.println(sum);"
        + "  }\n"
        + "}";
    assertEditsResult(code, List.of(edit), expectedResult);
  }

}
