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
    ),
    "identifierUrls", List.of(
      "http://adnovum.ch/nevisidm/ws",
      "http://auth0.com/oauth/grant-type/password-realm",
      "http://bigcontent.io/cms/schema",
      "http://cv.iptc.org/newscodes/digitalsourcetype",
      "http://dashif.org/guidelines",
      "http://dashif.org/thumbnail_tile",
      "http://data.europa.eu/esco",
      "http://data.europa.eu/p27",
      "http://dbpedia.org/ontology",
      "http://dbpedia.org/property",
      "http://dbpedia.org/resource",
      "http://hapifhir.io/fhir",
      "http://macedir.org/entity-category",
      "http://metadatahub.bijnpo.nl/graph",
      "http://neverssl.com",
      "http://osmand.net/",
      "http://pds.nasa.gov/pds4",
      "http://powerbi.com/product/schema",
      "http://purl.oclc.org/ooxml",
      "http://snomed.info/sct",
      "http://terminology.hl7.org/CodeSystem",
      "http://tizen.org/feature",
      "http://tizen.org/system",
      "http://www.ebu.ch/metadata/ontologies",
      "http://www.garmin.com/xmlschemas",
      "http://www.iab.com/VAST",
      "http://www.ifxforum.org/IFX_150",
      "http://www.naxml.org/POSBO",
      "http://www.nlm.nih.gov/research/umls/rxnorm",
      "http://www.openarchives.org/OAI",
      "http://www.pcats.org/schema",
      "http://www.sat.gob.gt/face2",
      "http://www.sitemaps.org/schemas",
      "http://www.topografix.com/GPX",
      "HTTP://WWW.TOPOGRAFIX.COM/GPX/1/1",
      " \thttp://auth0.com/oauth/grant-type/password-realm\n",
      "http://neverssl.com?query=value",
      "http://osmand.net#identifier",
      "http://www.topografix.com/GPX?version=1#route",
      "http://www.topografix.com/GPXextra",
      "http://neverssl.com/",
      "http://osmand.net?mode=extensions#schema",
      "  http://www.topografix.com/GPX  "
    ),
    "nonIdentifierUrls", List.of(
      "http://identifier.invalid",
      "https://auth0.com/oauth/grant-type",
      "ftp://auth0.com/oauth/grant-type",
      "auth0.com/oauth/grant-type",
      "http://auth0.com",
      "http://auth0.com?oauth/grant-type",
      "http://auth0.com#oauth/grant-type",
      "http://auth0.com/api",
      "http://metadatahub.bijnpo.nl/api",
      "http://auth0.com/OAUTH/grant-type",
      "http://www.topografix.com/gpx",
      "http://auth0.com:80/oauth/grant-type",
      "http://auth0.com:8080/oauth/grant-type",
      "http://sub.auth0.com/oauth/grant-type",
      "http://auth0.com./oauth/grant-type",
      "http://www.topografix.com.anotherdomain.org/GPX/1/1",
      "http://auth0.com.anotherdomain.org/oauth/grant-type/password-realm",
      "http://user@auth0.com/oauth/grant-type",
      "http://auth0.com@anotherdomain.org/oauth/grant-type",
      "http://auth0.com/oauth/grant-type extra",
      "http://auth0.com/oauth/grant-type\tmore",
      "http://auth0.com/oauth/grant-type\nmore",
      "http://auth0.com/oauth/grant-type\\extra",
      "http://auth0.com\\other/oauth/grant-type",
      "http://auth0.com/%6fauth/grant-type",
      "http://auth0.com/other/../oauth/grant-type",
      ""
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
