/*
 * SonarSource Analyzers XML Parsing Commons
 * Copyright (C) SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * You can redistribute and/or modify this program under the terms of
 * the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
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

import com.ctc.wstx.api.WstxInputProperties;
import javax.xml.stream.XMLInputFactory;

public class SafeStaxParserFactory {

  private SafeStaxParserFactory() {
    // class with static methods only
  }

  public static XMLInputFactory createXMLInputFactory() {
    // forcing the XMLInputFactory implementation class, in order to be sure that we are going to use the adequate
    // stream reader while retrieving locations
    XMLInputFactory factory = new com.ctc.wstx.stax.WstxInputFactory();

    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
    factory.setProperty(XMLInputFactory.IS_VALIDATING, false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    factory.setProperty(WstxInputProperties.P_MAX_ATTRIBUTE_SIZE, Integer.MAX_VALUE);

    return factory;
  }

}
