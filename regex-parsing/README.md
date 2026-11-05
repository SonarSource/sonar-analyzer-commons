SonarSource Analyzers Regular Expressions (REGEX) parser
=========================
Classes required to parse regular expressions from Java code

### Upgrading: Java POSIX character classes

S5855 now detects redundant alternatives involving Java POSIX properties such as
`\p{Space}|\n` and `\p{Lower}|a`. Analyzers upgrading this library may need to
update their rule test expectations. Unicode properties and version-dependent
case-insensitive `Lower`/`Upper` matches are handled conservatively.

### License
Copyright 2009-2023 SonarSource.
Licensed under the [GNU Lesser General Public License, Version 3.0](http://www.gnu.org/licenses/lgpl.txt)
