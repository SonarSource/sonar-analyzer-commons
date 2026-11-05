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
package org.sonarsource.analyzer.commons.appsec.export;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Generates the shared regression corpus from build-time sample lists.
 * This module's tests verify the samples against the Java filter before they are exported.
 */
public final class CleartextProtocolCorpusExporter {

  static final Map<String, List<String>> URL_SAMPLES = Map.of(
    "safeUrls", List.of(
      "http://www.w3.org/2000/svg",
      "HTTP://WWW.W3.ORG/2000/SVG",
      "http://www.w3.org:8080/2000/svg",
      "http://www.w3.org",
      "http://www.w3.org?query=value#fragment",
      "  http://www.w3.org/2000/svg  ",
      "http://www.mulesoft.org/schema",
      "http://www.eclipse.org/emf",
      "http://jabber.org/protocol/muc",
      "HTTP://JABBER.ORG/PROTOCOL/MUC",
      "http://jabber.org/protocol/<name>",
      "http://apache.org/xml/features/disallow-doctype-decl",
      "http://localhost:8080/api",
      "http://localhost:<port>",
      "http://127.1/api",
      "http://0x7f000001/api",
      "http://[::1]/api",
      "http://169.254.169.254/latest/meta-data",
      "http://10.0.2.2/api",
      "http://metadata.google.internal/api",
      "http://service.ns.svc.cluster.local",
      "http://example.org/api",
      "http://sub.example.org/api",
      "http://service.test/api",
      "http://my-service/api",
      "https://insecure.com",
      "not-a-url",
      ""
    ),
    "unsafeUrls", List.of(
      "http://insecure.com",
      "ftp://insecure.com",
      "ws://insecure.com",
      "http://www.w3.org.anotherdomain.org/2000/svg",
      "http://localhost.anotherdomain.org",
      "http://example.org.anotherdomain.org",
      "http://jabber.org/",
      "http://jabber.org/other",
      "http://jabber.org:80/protocol/muc",
      "http://jabber.org/other/<name>",
      "http://apache.org/xml/featuresx/disallow-doctype-decl",
      "http://user:pass@jabber.org/protocol/muc",
      "http://www.w3.org@anotherdomain.org/2000/svg",
      "http://10.0.3.2/api",
      "http://2130706433",
      "http://0x08080808",
      "http://[2001:db8::1]",
      "http://metadatahub.bijnpo.nl/api"
    )
  );

  private CleartextProtocolCorpusExporter() {
  }

  public static void main(String[] args) {
    if (args.length < 1) {
      throw new IllegalArgumentException("Usage: CleartextProtocolCorpusExporter <output-json-path>");
    }
    JsonExports.write(args[0], toJson());
  }

  public static String toJson() {
    JsonObject root = new JsonObject();
    new TreeMap<>(URL_SAMPLES)
      .forEach((group, samples) -> root.add(group, JsonExports.toArray(samples)));
    return JsonExports.render(root);
  }
}
