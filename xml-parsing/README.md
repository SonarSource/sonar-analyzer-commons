SonarSource Analyzer XML Parsing Commons
=========================

* [Parsing XML](#parsing)
* [Providing safe parser](#safeParser)
* [Writing a check for XML file](#writingCheck)


## <a name="parsing"></a>To parse XML

* `XmlFile.create(<content string>)`
* `XmlFile.create(<InputFile>)`
 
Returned `XmlFile` object keeps parsed document. Call `XmlFile#getDocument()` to obtain `Document` object. Parser sets location ranges on `Document` nodes as user data (see `Document#getUserData(String)`).
Each node has `NODE` storing the whole node range.
Then depending on a node type, other kinds of locations might be available.

### Simple element
```
<foo> </foo>
 ^^^            NAME
^^^^^           START
      ^^^^^^    END
^^^^^^^^^^^^    NODE
```

### Self-closing element
```
<foo />
 ^^^         NAME
^^^^^^^      START
^^^^^^^      END
^^^^^^^      NODE
```

### Attribute
```
attr = "hello"
^^^^            NAME
       ^^^^^^^  VALUE
^^^^^^^^^^^^^^  NODE
```

### Text
```
<foo>Hello world</foo>
     ^^^^^^^^^^^            NODE
```

### Comment
```
<foo><!-- comment --></foo>
     ^^^^^^^^^^^^^^^^            NODE
```

### CDATA
```
<![CDATA[ ... ]]>
^^^^^^^^^             START
              ^^^     END
^^^^^^^^^^^^^^^^^     NODE
```

### Doctype
```
<!DOCTYPE ... >
^^^^^^^^^           START
              ^     END
^^^^^^^^^^^^^^^     NODE
```

To retrieve such ranges use these static util methods of `XmlFile`:
* `XmlFile.nodeLocation(Node)`
* `XmlFile.startLocation(Element)`
* `XmlFile.endLocation(Element)`
* `XmlFile.nameLocation(Element)`
* `XmlFile.startLocation(CDataSection)`
* `XmlFile.endLocation(CDataSection)`
* `XmlFile.attributeNameLocation(Attr)`
* `XmlFile.attributeValueLocation(Attr)`
* `XmlFile.getRange(Node, Location)`

### Prolog (see `PrologElement` API)
```
<?xml version="1.0" ?>
^^^^^                  start
      ^^^^^^^          attribute name
              ^^^^^    attribute value
                    ^^ end
```


## <a name="safeParser"></a>To instantiate safe parser
If source locations are not needed, use the safe parser factories directly.
They disable external entity loading.
* `SafeStaxParserFactory.createXMLInputFactory()`
* `SafeDomParserFactory.createDocumentBuilder(boolean namespaceAware)`

`SafetyFactory` is a deprecated adapter.

## <a name="writingCheck"></a>To write a check for XML file
Implement a class extending `SonarXmlCheck`.

```
public class FileCheck extends SonarXmlCheck {

  @Override
  public void scanFile(XmlFile file) {
    reportIssueOnFile("File level message", Lists.newArrayList(1, 2));
  }
}
```

If you want to write a check which will rely on XPath, implement a class extending `SimpleXPathBasedCheck`.
This class provides numerous methods to simplify writing checks which will query DOM.

```
public class MyXPathCheck extends SimpleXPathBasedCheck {

  // all the <a> nodes of a DOM, whatever their position in the tree
  private final XPathExpression myXPathExpressin = getXPathExpression("//a");

  @Override
  public void scanFile(XmlFile file) {
    List<Node> aNodes = evaluateAsList(myXPathExpressin, file.getDocument());
    for (Node aNode : aNodes) {
      reportIssue(aNode, "Issue on each node <a> of the DOM.");
    }
  }
}
```

### License
Copyright 2009-2023 SonarSource.
Licensed under the [GNU Lesser General Public License, Version 3.0](http://www.gnu.org/licenses/lgpl.txt)
