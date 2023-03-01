/*
 * SonarSource Analyzers Commons
 * Copyright (C) 2009-2023 SonarSource SA
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
package org.sonarsource.analyzer.commons;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProfileGeneratorTest {

  @Test
  public void test_generation_of_profile() throws Exception {
    ProfileGenerator.RulesConfiguration rulesConfiguration = new ProfileGenerator.RulesConfiguration()
      .add("S1451", "headerFormat", "// Copyright 20\\d\\d The Closure Library Authors. All Rights Reserved.")
      .add("S1451", "isRegularExpression", "true")
      .add("S2762", "threshold", "1");
    Set<String> rules = new HashSet<>(Arrays.asList("S1451", "S2762", "S101"));
    File profile = ProfileGenerator.generateProfile("js", "javascript", rulesConfiguration, rules);
    String profileAsString = Files.readAllLines(profile.toPath()).stream().collect(Collectors.joining());
    assertThat(profileAsString).isEqualTo("<?xml version=\"1.0\" encoding=\"UTF-8\"?><profile><name>rules</name><language>js</language><rules>" +
      "<rule><repositoryKey>javascript</repositoryKey><key>S101</key><priority>INFO</priority></rule>" +
      "<rule><repositoryKey>javascript</repositoryKey><key>S1451</key><priority>INFO</priority>" +
      "<parameters><parameter><key>headerFormat</key><value>// Copyright 20\\d\\d The Closure Library Authors. All Rights Reserved.</value></parameter>" +
      "<parameter><key>isRegularExpression</key><value>true</value></parameter></parameters></rule>" +
      "<rule><repositoryKey>javascript</repositoryKey><key>S2762</key><priority>INFO</priority><parameters><parameter><key>threshold</key><value>1</value></parameter></parameters></rule>" +
      "</rules></profile>");
  }

  @Test
  public void test_connection() throws Exception {
    ProfileGenerator.RulesConfiguration rulesConfiguration = new ProfileGenerator.RulesConfiguration();
    ServerSocket mockServer = new ServerSocket(0);
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    executorService.submit(() -> {
      Socket socket = mockServer.accept();
      InputStream inputStream = socket.getInputStream();
      String request = new BufferedReader(new InputStreamReader(inputStream)).readLine();
      assertThat(request).contains("p=1&ps=500&languages=js&repositories=javascript");
      OutputStream out = socket.getOutputStream();
      String content = "{\"total\":1,\"p\":1,\"ps\":500,\"rules\":[{\"key\":\"javascript:S3798\"}]}";
      String response = "HTTP/1.1 200 OK\n" +
        "Content-Length: " + content.length() + "\n" +
        "Content-Type: text/html\n\n" +
        content;

      out.write(response.getBytes(StandardCharsets.UTF_8));
      out.close();
      socket.close();
      return null;
    });
    File file = ProfileGenerator.generateProfile("http://localhost:" + mockServer.getLocalPort(), "js", "javascript", rulesConfiguration, Collections.emptySet());
    String response = Files.readAllLines(file.toPath()).stream().collect(Collectors.joining());
    assertThat(response).contains("S3798");
    mockServer.close();
  }
}
