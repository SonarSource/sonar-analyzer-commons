SonarSource Analyzer Test Commons
=========================

### Noncompliant Format

Assert an issue with the comment on the same line.
```js
alert(msg); // Noncompliant
```

Assert an issue with the comment on a different line.
```js
// Noncompliant@+1
alert(msg);
```

Assert an issue with a message and/or an `effortToFix`.
```js
alert(msg); // Noncompliant {{Here the expected error message}} [[effortToFix=2]]
```

Assert several issues on the same line.
```js
alert(msg); // Noncompliant 2

alert(msg); // Noncompliant {{Expected error message 1}} {{Expected error message 2}}
```

Assert precise primary location of an issue with the comment on a line below containing required number of `^`
```js
  alert(msg); // Noncompliant {{Rule message}}
//      ^^^
```

Assert precise primary and secondary locations of an issue. Note, that when testing secondary locations *precise* primary location is **required**.
```js
  alert(msg);
//      ^^^> {{Secondary location message1}}
  alert(msg); // Noncompliant {{Rule message}}
//      ^^^
  alert(msg);
//      ^^^< {{Secondary location message2}}
```

Assert several secondary locations on the same line using `@-1`/`@+1` syntax.
```js
  alert(msg); // Noncompliant {{Rule message}}
//^^^^^
//     ^^^^^@-1< {{Secondary location message1}}
//          ^@-2< {{Secondary location message2}}
```

Assert multiline primary or secondary location with following syntax. 
For it you need only a single `^` pointing at the start column of location. 
Position of `^` will be ignored when `sc` (start column) property is provided. 
When no `@+X`/`@-X` is provided, line above comment will be considered as first line of location. 
If `@+X`/`@-X` is provided, shift is done relatively to this line above. `+/-` in `el` (end line) will be considered relatively to start line of location.
`ec` stands for end column.
```js
// issue is on both lines here
alert(msg); // Noncompliant {{Rule message}}
alert(msg);
//^[sc=1;el=+1;ec=11]@-1
```

Same issue in a bit another way:
```js
// issue is on both lines here
  alert(msg); // Noncompliant {{Rule message}}
//^[el=+2;ec=13]
  alert(msg);
```

Example of this syntax used for a single line primary location (can be useful if you can't indent code to fit comment start) and also secondary location:
```js
alert(msg); // Noncompliant {{Rule message}}
//^[sc=1;el=+0;ec=11]

alert(msg);
alert(msg);
//^[sc=1;el=+1;ec=11]@-1< {{Secondary message}}
```

### Usage at Verifier level

1. Initialisation, there's two main use cases:

   1. SingleFileVerifier

      When a language analyzer only tests a single source code file at a time and could report issues only on that file,
      you should create: `SingleFileVerifier verifier = SingleFileVerifier.create(path, UTF_8);`

   2. MultiFileVerifier

      When a language analyzer tests a main source code file that can include other files and report issues on several files,
      you should create: `MultiFileVerifier verifier = MultiFileVerifier.create(path, UTF_8);`

2. Collecting comments

   Report all parsed source code comments with the method `verifier.addComment(...)`

3. Collecting issues

   Report all issue raised by the rule with the method `verifier.reportIssue(...)`

4. Assert that actual issues match expected issues

   End the unit test by calling `verifier.assertOneOrMoreIssues()` or `verifier.assertNoIssues()` 

#### Usage example

This example use `SingleFileVerifier`, but it would be quite the same with `MultiFileVerifier` except for the `path` parameter that is needed for some verifier methods.

```java
public class MyVerifier {

  private static final Path BASE_DIR = Paths.get("src/test/resources/check/");
  private static final int COMMENT_PREFIX_LENGTH = 2;
  private static final int COMMENT_SUFFIX_LENGTH = 0;

  public static void verify(String relativePath, RuleCheck check) {
    createVerifier(relativePath, check).assertOneOrMoreIssues();
  }

  public static void verifyNoIssue(String relativePath, RuleCheck check) {
    createVerifier(relativePath, check).assertNoIssues();
  }

  private static SingleFileVerifier createVerifier(String relativePath, RuleCheck check) {
    Path path = BASE_DIR.resolve(relativePath);
    SingleFileVerifier verifier = SingleFileVerifier.create(path, UTF_8);
    /* then we scan the file to collect: 1) comment, 2) issues raised by the rule check ... */
    {
      // 1) For each comment call "addComment(...)":
      verifier.addComment(token.line, token.column, token.value, COMMENT_PREFIX_LENGTH, COMMENT_SUFFIX_LENGTH);

      // 2) For each issue raised by a rule call "reportIssue(...)":
      //   2.1) issue on file
      verifier.reportIssue("This file has a bad name").onFile();

      //   2.2) issues on line
      verifier.reportIssue("This line is too long").onLine(line);
      verifier.reportIssue("This line costs a lot").onLine(line).withGap(2.5d);

      //   2.3) issue on range
      verifier.reportIssue("Invalid name.").onRange(line, column, endLine, endColumn);
      
      //   2.3) issue with a secondary location
      verifier.reportIssue("Already initialized").onRange(line, column, endLine, endColumn)
        .addSecondary(secondary.line, secondary.column, secondary.endLine, secondary.endColumn, "Original");

      //   2.4) issue with flow
      verifier.reportIssue("Always true").onRange(line, column, endLine, endColumn)
        .addFlow(flow1_1.line, flow1_1.column, flow1_1.endLine, flow1_1.endColumn, 1, "Set to null")
        .addFlow(flow1_2.line, flow1_2.column, flow1_2.endLine, flow1_2.endColumn, 1, "Always null")
        .addFlow(flow2_1.line, flow2_1.column, flow2_1.endLine, flow2_1.endColumn, 2, "Is not null");
    }
    return verifier;
  }

}
```

### License
Copyright 2009-2017 SonarSource.
Licensed under the [GNU Lesser General Public License, Version 3.0](http://www.gnu.org/licenses/lgpl.txt)
