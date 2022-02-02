/*
 * SonarSource Analyzers Regex Parsing Commons
 * Copyright (C) 2009-2021 SonarSource SA
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
package org.sonarsource.analyzer.commons.regex.finders;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import junit.framework.AssertionFailedError;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.comments.CommentLine;
import org.snakeyaml.engine.v2.common.ScalarStyle;
import org.snakeyaml.engine.v2.composer.Composer;
import org.snakeyaml.engine.v2.exceptions.Mark;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.SequenceNode;
import org.snakeyaml.engine.v2.parser.Parser;
import org.snakeyaml.engine.v2.parser.ParserImpl;
import org.snakeyaml.engine.v2.scanner.ScannerImpl;
import org.snakeyaml.engine.v2.scanner.StreamReader;
import org.sonarsource.analyzer.commons.checks.verifier.SingleFileVerifier;
import org.sonarsource.analyzer.commons.checks.verifier.internal.IssueLocation;
import org.sonarsource.analyzer.commons.regex.RegexFeature;
import org.sonarsource.analyzer.commons.regex.RegexIssueLocation;
import org.sonarsource.analyzer.commons.regex.RegexParseResult;
import org.sonarsource.analyzer.commons.regex.RegexParser;
import org.sonarsource.analyzer.commons.regex.ast.FlagSet;
import org.sonarsource.analyzer.commons.regex.ast.IndexRange;
import org.sonarsource.analyzer.commons.regex.ast.RegexSyntaxElement;
import org.sonarsource.analyzer.commons.regex.php.PhpRegexFlags;
import org.sonarsource.analyzer.commons.regex.php.PhpRegexSource;

import static java.nio.charset.StandardCharsets.UTF_8;

public final class RegexFinderVerifier {

  private SingleFileVerifier verifier;

  public void verify(FinderCheck check, Path path) {
    List<Node> nodes = parse(readFile(path));
    verifier = createVerifier(path, nodes);
    new RegexVisitor(check).visit(nodes);
    verifier.assertOneOrMoreIssues();
  }

  private static SingleFileVerifier createVerifier(Path path, List<Node> nodes) {
    SingleFileVerifier verifier = SingleFileVerifier.create(path, StandardCharsets.UTF_8);
    CommentVisitor commentVisitor = new CommentVisitor();
    commentVisitor.visit(nodes);
    commentVisitor.comments.stream()
      .sorted(Comparator.comparingInt(commentLine -> commentLine.getStartMark().get().getLine()))
      .forEach(comment ->
        comment.getStartMark().ifPresent(mark ->
          verifier.addComment(mark.getLine() + 1, mark.getColumn() + 1, comment.getValue(), 1, 0)));
    return verifier;
  }

  private static List<Node> parse(String source) {
    LoadSettings settings = LoadSettings.builder().setParseComments(true).build();
    StreamReader reader = new StreamReader(settings, source);
    ScannerImpl scanner = new ScannerImpl(settings, reader);
    Parser parser = new ParserImpl(settings, scanner);
    Composer composer = new Composer(settings, parser);
    return composerNodes(composer);
  }

  private static List<Node> composerNodes(Composer composer) {
    List<Node> nodes = new ArrayList<>();
    while (composer.hasNext()) {
      nodes.add(composer.next());
    }
    if (nodes.size() > 1 || !(nodes.get(0) instanceof SequenceNode)) {
      throw new AssertionFailedError("Test does not have the expected format of a single list.");
    }
    return nodes;
  }

  private static String readFile(Path path) {
    try {
      return new String(Files.readAllBytes(path), UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read " + path, e);
    }
  }

  static class NodeVisitor {
    public void visit(List<Node> nodes) {
      for (Node node: nodes) {
        visit(node);
      }
    }

    protected void visit(Node node) {
      if (node instanceof SequenceNode) {
        visitSequence((SequenceNode) node);
      } else if (node instanceof MappingNode) {
        visitMapping((MappingNode) node);
      } else if (node instanceof ScalarNode) {
        visitScalarNode((ScalarNode) node);
      }
    }

    protected void visitScalarNode(ScalarNode node) {
      // do nothing
    }

    protected void visitSequence(SequenceNode node) {
      visit(node.getValue());
    }

    protected void visitMapping(MappingNode node) {
      node.getValue().forEach(this::visitTuple);
    }

    protected void visitTuple(NodeTuple node) {
      visit(node.getKeyNode());
      visit(node.getValueNode());
    }
  }

  class RegexVisitor extends NodeVisitor {

    final FinderCheck check;
    Set<RegexFeature> features = new HashSet<>();

    public RegexVisitor(FinderCheck check) {
      this.check = check;
    }

    public void reportRegexTreeIssue(RegexSyntaxElement syntaxElement, String message, @Nullable Integer cost, List<RegexIssueLocation> secondaries) {
      VerifierRegexSource source = (VerifierRegexSource) syntaxElement.getSource();
      reportIssue(source.issueLocationInFile(syntaxElement.getRange()), message, secondaries);
    }

    public void reportInvocationTreeIssue(Node regexNode, String message, @Nullable Integer cost, List<RegexIssueLocation> secondaries) {
      Mark startMark = regexNode.getStartMark().get();
      Mark endMark = regexNode.getEndMark().get();
      IssueLocation.Range range = new IssueLocation.Range(null, startMark.getLine() + 1, startMark.getColumn(), endMark.getLine() + 1, endMark.getColumn());
      reportIssue(range, message, secondaries);
    }

    private void reportIssue(IssueLocation.Range range, String message, List<RegexIssueLocation> secondaries) {
      verifyHighlightableRange(range);
      SingleFileVerifier.Issue issue = verifier
        .reportIssue(message)
        .onRange(range.getLine(), range.getColumn(), range.getEndLine(), range.getEndColumn());
      secondaries.forEach(location -> {
        IssueLocation.Range secondaryRange = issueLocationInFile(location);
        verifyHighlightableRange(range);
        issue.addSecondary(secondaryRange.getLine(), secondaryRange.getColumn(), secondaryRange.getEndLine(),
          secondaryRange.getEndColumn(), location.message());
      });
    }

    private void verifyHighlightableRange(IssueLocation.Range range) {
      if (range.getLine() == range.getEndLine() && range.getColumn() == range.getEndColumn()) {
        throw new AssertionFailedError(String.format("Fail to report location on line %d which has no highlightable range", range.getLine()));
      }
    }

    @Override
    protected void visitScalarNode(ScalarNode node) {
      checkRegex(node, null);
    }

    @Override
    protected void visitTuple(NodeTuple node) {
      if (node.getKeyNode() instanceof ScalarNode && node.getValueNode() instanceof ScalarNode) {
        ScalarNode keyNode = (ScalarNode) node.getKeyNode();
        ScalarNode valueNode = ((ScalarNode) node.getValueNode());
        if ("features".equals(keyNode.getValue())) {
          setFeatures(valueNode.getValue());
        } else {
          checkRegex(keyNode, valueNode);
        }
      }
    }

    private void checkRegex(ScalarNode regexNode, @Nullable ScalarNode flagNode) {
      RegexParseResult parseResult = parseRegex(regexNode, flagNode);
      check.checkRegex(parseResult, this::reportRegexTreeIssue, (message, cost, secondaries) -> reportInvocationTreeIssue(regexNode, message, cost, secondaries));
    }

    private void setFeatures(String features) {
      this.features.clear();
      features = features.replaceAll("\\s+", "");
      Arrays.stream(features.split("\\|")).forEach(f -> {
        if (f.startsWith("^")) {
          processFeature(f.substring(1), this.features::remove);
        } else {
          processFeature(f, this.features::add);
        }
      });
    }

    private void processFeature(String feature, Consumer<RegexFeature> consumer) {
      if ("ALL".equals(feature)) {
        Arrays.asList(RegexFeature.values()).forEach(consumer);
      } else {
        consumer.accept(getFeature(feature));
      }
    }

    private RegexFeature getFeature(String feature) {
      try {
        return RegexFeature.valueOf(feature);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(String.format("Feature '%s' is not supported or misspelled", feature), e.getCause());
      }
    }

    private RegexParseResult parseRegex(ScalarNode regexNode, @Nullable ScalarNode flagNode) {
      char quote = regexNode.getScalarStyle() == ScalarStyle.SINGLE_QUOTED ? '\'' : '"';
      FlagSet flagSet = flagNode != null ? PhpRegexFlags.parseFlags(flagNode.getValue()) : new FlagSet();
      return new RegexParser(new VerifierRegexSource(regexNode, quote, features), flagSet).parse();
    }
  }


  static class CommentVisitor extends NodeVisitor {

    List<CommentLine> comments = new ArrayList<>();

    @Override
    protected void visit(Node node) {
      comments.addAll(node.getBlockComments());
      comments.addAll(node.getInLineComments());
      if (node.getEndComments() != null) {
        comments.addAll(node.getEndComments());
      }
      super.visit(node);
    }
  }

  static class VerifierRegexSource extends PhpRegexSource {
    final int sourceLine;
    final int sourceStartOffset;
    final Set<RegexFeature> features;

    VerifierRegexSource(ScalarNode node, char quote, Set<RegexFeature> features) {
      super(getRegex(node), quote);
      this.features = features;
      Mark startMark = node.getStartMark().get();
      sourceLine = startMark.getLine() + 1;
      sourceStartOffset = startMark.getColumn() + 1;
    }

    static String getRegex(ScalarNode node) {
      if (node.getStartMark().get().getLine() != node.getEndMark().get().getLine()) {
        throw new AssertionFailedError("Regex is not a single line string");
      }
      if (node.isPlain()) {
        throw new AssertionFailedError(String.format("The regular expression string in line %s should not be a plain string.", node.getStartMark().get().getLine() + 1));
      }
      return node.getValue();
    }

    IssueLocation.Range issueLocationInFile(IndexRange range) {
      return new IssueLocation.Range(null, sourceLine, sourceStartOffset + range.getBeginningOffset(),
        sourceLine, sourceStartOffset + range.getEndingOffset());
    }

    @Override
    public Set<RegexFeature> features() {
      if (features.isEmpty()) {
        return super.features();
      }
      return features;
    }
  }

  IssueLocation.Range issueLocationInFile(RegexIssueLocation location) {
    RegexSyntaxElement firstElement = location.syntaxElements().get(0);
    IndexRange range = firstElement.getRange();
    location.syntaxElements().stream().skip(1).map(RegexSyntaxElement::getRange).forEach(range::merge);
    VerifierRegexSource source = (VerifierRegexSource) firstElement.getSource();
    return source.issueLocationInFile(range);
  }
}
