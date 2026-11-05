/*
 * SonarSource Analyzers Commons Configurations
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
import { execFileSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';

const [archiveArgument, exclusionsArgument, corpusArgument] = process.argv.slice(2);
const archive = resolve(archiveArgument);
const exclusions = resolve(exclusionsArgument);
const corpus = resolve(corpusArgument);
const consumerDirectory = mkdtempSync(join(dirname(archive), 'npm-import-test-'));
const packageDirectory = join(
  consumerDirectory, 'node_modules', '@sonarsource', 'analyzer-commons-configurations',
);
mkdirSync(packageDirectory, { recursive: true });

// Test the assembled artifact, not the loose generated-resources files.
execFileSync('tar', ['-xzf', archive, '-C', packageDirectory, '--strip-components=1']);

const consumer = join(consumerDirectory, 'consumer.mjs');
writeFileSync(consumer, `
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import exclusions from '@sonarsource/analyzer-commons-configurations/cleartext-protocol-exclusions.json' with { type: 'json' };
import corpus from '@sonarsource/analyzer-commons-configurations/cleartext-protocol-corpus.json' with { type: 'json' };
import manifest from '@sonarsource/analyzer-commons-configurations/package.json' with { type: 'json' };

assert.equal(manifest.name, '@sonarsource/analyzer-commons-configurations');
assert.ok(manifest.files.includes('cleartext-protocol-exclusions.json'));
assert.ok(manifest.files.includes('cleartext-protocol-corpus.json'));
assert.deepEqual(Object.keys(exclusions), ['match', 'cleartextProtocols', 'hostPatterns', 'safeUrlPrefixes']);
assert.ok(exclusions.cleartextProtocols.includes('http://'));
assert.deepEqual(Object.keys(exclusions.hostPatterns), ['documentation', 'internal', 'namespace']);
assert.ok(exclusions.safeUrlPrefixes.includes('http://jabber.org/protocol/'));
assert.deepEqual(Object.keys(corpus), ['safeUrls', 'unsafeUrls']);
assert.ok(corpus.safeUrls.length > 0);
assert.ok(corpus.unsafeUrls.length > 0);
assert.deepEqual(exclusions, JSON.parse(readFileSync(process.argv[2], 'utf8')));
assert.deepEqual(corpus, JSON.parse(readFileSync(process.argv[3], 'utf8')));
`);
execFileSync(process.execPath, [consumer, exclusions, corpus], { stdio: 'inherit' });
console.log('Assembled npm package imports match the Java exports.');
