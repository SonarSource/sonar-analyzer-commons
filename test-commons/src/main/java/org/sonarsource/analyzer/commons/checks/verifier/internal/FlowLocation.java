/*
 * SonarSource Analyzers Test Commons
 * Copyright (C) 2009-2025 SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonarsource.analyzer.commons.checks.verifier.internal;

import javax.annotation.Nullable;

public class FlowLocation extends SecondaryLocation {

  public final int flowIndex;

  public final int indexInTheFlow;

  public FlowLocation(UnderlinedRange range, boolean primaryIsBefore, int flowIndex, int indexInTheFlow, @Nullable String message) {
    super(range, primaryIsBefore, flowIndex, message);
    this.flowIndex = flowIndex;
    this.indexInTheFlow = indexInTheFlow;
  }

  @Override
  public void write(int indent, StringBuilder out, boolean primaryIsWritten) {
    range.underline(indent, out);
    out.append(primaryIsWritten ? '<' : '>');
    out.append(' ').append(flowIndex).append('.').append(indexInTheFlow);
    if (message != null) {
      out.append(" {{").append(message).append("}}");
    }
  }

}
