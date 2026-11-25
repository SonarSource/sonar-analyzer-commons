/*
 * SonarSource Analyzers Commons Configurations
 * Copyright (C) SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License version 3 as published by the Free Software Foundation.
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

#![no_std]

//! Shared analyzer configuration and validation corpora generated from
//! sonar-analyzer-commons for non-JVM SonarSource analyzers.
//!
//! The exported values are JSON strings. This crate intentionally supplies
//! data only; consumers parse the JSON and implement matching semantics in
//! their own runtime.

pub const SECRET_PATTERNS_JSON: &str = include_str!("../data/secret-patterns.json");
pub const SECRET_EXCLUSION_CORPUS_JSON: &str =
    include_str!("../data/secret-exclusion-corpus.json");
pub const CLEARTEXT_PROTOCOL_EXCLUSIONS_JSON: &str =
    include_str!("../data/cleartext-protocol-exclusions.json");
pub const CLEARTEXT_PROTOCOL_CORPUS_JSON: &str =
    include_str!("../data/cleartext-protocol-corpus.json");
