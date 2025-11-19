/*
 * SonarSource Analyzers XML Parsing Commons
 * Copyright (C) 2009-2025 SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonarsource.analyzer.commons.xml;

import java.util.List;

public class PrologElement {

  private List<PrologAttribute> attributes;
  private XmlTextRange prologStartLocation;
  private XmlTextRange prologEndLocation;

  public PrologElement(List<PrologAttribute> attributes, XmlTextRange prologStartLocation, XmlTextRange prologEndLocation) {
    this.attributes = attributes;
    this.prologStartLocation = prologStartLocation;
    this.prologEndLocation = prologEndLocation;
  }

  public List<PrologAttribute> getAttributes() {
    return attributes;
  }

  public XmlTextRange getPrologStartLocation() {
    return prologStartLocation;
  }

  public XmlTextRange getPrologEndLocation() {
    return prologEndLocation;
  }

  public static class PrologAttribute {
    XmlTextRange nameLocation;
    String name;
    XmlTextRange valueLocation;
    String value;

    public PrologAttribute(String name, XmlTextRange nameLocation, String value, XmlTextRange valueLocation) {
      this.nameLocation = nameLocation;
      this.name = name;
      this.valueLocation = valueLocation;
      this.value = value;
    }

    public XmlTextRange getNameLocation() {
      return nameLocation;
    }

    public String getName() {
      return name;
    }

    public XmlTextRange getValueLocation() {
      return valueLocation;
    }

    public String getValue() {
      return value;
    }

  }

}
