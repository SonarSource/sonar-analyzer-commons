/*
 * SonarQube Analyzer Commons
 * Copyright (C) 2009-2019 SonarSource SA
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
package org.sonarsource.analyzer.commons.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.sonarsource.analyzer.commons.RuleMetadataLoader;

/**
 * Annotate a rule class with this annotation in order to provide deprecated rule keys
 * (see {@link org.sonar.api.server.rule.RulesDefinition.NewRule#addDeprecatedRuleKey(String, String)}).
 * This annotation will make effect only when used with {@link RuleMetadataLoader}.
 * Repository key can be omitted, then the current repository will be used.
 * If there are several deprecated rule keys, put several annotations one after another.
 */
@Repeatable(DeprecatedRuleKeys.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DeprecatedRuleKey {

  String repositoryKey() default "";

  String ruleKey();

}

