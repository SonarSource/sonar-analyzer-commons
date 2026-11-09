# XML check test helper

`SonarXmlCheckVerifier` connects `SonarXmlCheck`, the scanner test fixtures,
`XmlFile`, and `SingleFileVerifier`. Keep this adapter separate from production
XML parsing. Match issue location and message contracts from both APIs.

Start with `SonarXmlCheckVerifierTest` and its sample files. Verify a compliant
file and a file with expected issues when changing matching or diagnostics.
Keep deliberate sample formatting unchanged.

Run `mvn -pl test-xml-parsing -am test` from the root. `-am` supplies
the local `xml-parsing` and `test-commons` dependencies.
