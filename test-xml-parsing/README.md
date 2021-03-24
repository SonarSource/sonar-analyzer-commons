SonarSource Analyzers XML Parsing Test Commons
=========================

* [Testing a check for XML file](#testingCheck)

## <a name="testingCheck"></a>To test a check for XML file
Use `SonarXmlCheckVerifier`:
```
SonarXmlCheckVerifier.verifyIssueOnFile("file.xml", new FileTestCheck(), "File level message", 1, 2);
```

You can use comments notation from [test-commons](../test-commons) to assert issues information.
```
SonarXmlCheckVerifier.verifyIssues("checkTestFile.xml", testCheck);
```

> :exclamation: Tested XML files should be in directory `src/test/java/resources/checks/<CheckClassName>`

### License
Copyright 2009-2021 SonarSource.
Licensed under the [GNU Lesser General Public License, Version 3.0](http://www.gnu.org/licenses/lgpl.txt)
