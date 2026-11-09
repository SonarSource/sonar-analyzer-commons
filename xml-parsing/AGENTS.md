# XML parsing

`XmlFile.create` exposes the source and parsed DOM. `XmlParser` combines StAX
events and DOM nodes to attach source ranges. `XmlFile` provides the range
accessors. Preserve namespace behavior, comments, CDATA, and attribute ranges.

Use `SafeDomParserFactory` and `SafeStaxParserFactory` for parser creation.
They disable external entity loading. `SafetyFactory` is a deprecated adapter;
do not use it for new code. Test malformed XML, namespace handling, and entity
safety when changing parser setup.

`checks/SonarXmlCheck` and `checks/SimpleXPathBasedCheck` are rule entry points.
The test helper is in the separate `test-xml-parsing` module. This module also
declares `test-commons` as a provided dependency, so use `-am` in a scoped build.

Run `mvn -pl xml-parsing,test-xml-parsing -am test` from the root.
Use `XmlFileTest` and the adjacent parser/factory tests as references.
