/*
 * SonarSource Analyzers XML Parsing Commons
 * Copyright (C) 2009-2024 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarsource.analyzer.commons.xml.checks;

import org.junit.Test;
import org.sonarsource.analyzer.commons.xml.XmlFile;

import static org.assertj.core.api.Assertions.assertThat;


public class SonarXmlCheckTest {

  @Test
  public void check_context_null() {
    DummyCheck check = new DummyCheck();
    assertThat(check.getContext()).isNull();
  }

  static class DummyCheck extends SonarXmlCheck {
    @Override
    public void scanFile(XmlFile file) {
      reportIssueOnFile("message", null);
    }
  }

}