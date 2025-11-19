/*
 * SonarSource Performance Measure Library
 * Copyright (C) 2009-2025 SonarSource Sàrl
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonarsource.performance.measure;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

public final class DurationMeasureFiles {

  private static final Gson GSON_PRETTY = new GsonBuilder().setPrettyPrinting().create();
  private static final DecimalFormatSymbols SYMBOLS = DecimalFormatSymbols.getInstance(Locale.ROOT);
  private static final NumberFormat TIME_FORMAT = new DecimalFormat("0.000000000", SYMBOLS);
  private static final NumberFormat RANK_FORMAT = new DecimalFormat("000", SYMBOLS);

  private DurationMeasureFiles() {
    // utility class
  }

  public static void writeJson(Path path, DurationMeasure measure) throws IOException {
    Files.write(path, toJson(measure).getBytes(UTF_8));
  }

  public static String toJson(DurationMeasure measure) {
    String json = GSON_PRETTY.toJson(toJsonObject(measure));
    // reduce the number of lines by inlining some of the properties
    return json
      .replaceAll("\n *+(\"(?:name|calls|durationNanos|children)\":)", " $1")
      .replaceAll("(\\d)\n *+\\}", "$1 }");
  }

  public static JsonObject toJsonObject(DurationMeasure measure) {
    JsonObject jsonObject = new JsonObject();
    jsonObject.addProperty("name", measure.name());
    jsonObject.addProperty("calls", measure.calls());
    jsonObject.addProperty("durationNanos", measure.durationNanos());
    if (!measure.hasChildren()) {
      jsonObject.add("children", measure.sortedChildren().stream()
        .map(DurationMeasureFiles::toJsonObject)
        .collect(JsonArray::new, JsonArray::add, JsonArray::addAll));
    }
    return jsonObject;
  }

  public static DurationMeasure fromJsonWithoutObservationCost(Path performanceFile) throws IOException {
    DurationMeasure measure = fromJson(performanceFile);
    return MeasurementCost.subtractObservationCost(measure);
  }

  public static DurationMeasure fromJson(Path performanceFile) throws IOException {
    return fromJson(new String(Files.readAllBytes(performanceFile), UTF_8));
  }

  public static DurationMeasure fromJson(String json) {
    JsonObject jsonObject = GSON_PRETTY.fromJson(json, JsonObject.class);
    return fromJson(jsonObject);
  }

  public static DurationMeasure fromJson(JsonObject jsonObject) {
    Map<String, DurationMeasure> childrenMap = null;
    JsonArray children = jsonObject.getAsJsonArray("children");
    if (children != null && children.size() > 0) {
      childrenMap = new HashMap<>();
      for (JsonElement child : children) {
        DurationMeasure childMeasure = fromJson(child.getAsJsonObject());
        childrenMap.merge(childMeasure.name(), childMeasure, DurationMeasure::merge);
      }
    }
    String name = jsonObject.getAsJsonPrimitive("name").getAsString();
    long calls = jsonObject.getAsJsonPrimitive("calls").getAsLong();
    long durationNanos = jsonObject.getAsJsonPrimitive("durationNanos").getAsLong();
    return new DurationMeasure(name, calls, durationNanos, childrenMap);
  }

  /**
   * @param path the destination statistics file
   * @param measure the root of the measure hierarchy
   * @param categoryNames Map defining categories for grouped measures, when a measure.name() matches
   *                      a key of this map, the value of the map becomes the category of the measure
   *                      and its descendents.
   * @param groupedMeasurePredicate Predicate applied on each measure.name() to decide if the measure
   *                                needs to be extracted, grouped and categorised at the end of the
   *                                statistics file.
   */
  public static void writeStatistics(Path path, DurationMeasure measure, Map<String, String> categoryNames,
    Predicate<String> groupedMeasurePredicate) throws IOException {
    Files.write(path, toStatistics(measure, categoryNames, groupedMeasurePredicate).getBytes(UTF_8));
  }

  public static String toStatistics(DurationMeasure measure, Map<String, String> categoryNames, Predicate<String> groupedMeasurePredicate) {
    Map<String, Set<String>> measureCategories = categorizeMeasures(measure, categoryNames, Collections.emptySet());
    DurationMeasure measureWithoutGrouped = measure.copy();
    measureWithoutGrouped.remove(MeasurementCost.MEASUREMENT_COST_NAME);
    measureWithoutGrouped.remove(MeasurementCost.SUBTRACTED_MEASUREMENT_COST_NAME);
    Map<String, DurationMeasure> groupedMeasure = extractGroupedMeasure(measureWithoutGrouped, groupedMeasurePredicate);
    StringBuilder stat = new StringBuilder();
    stat.append("Performance (in seconds without observation cost)\n");
    stat.append(toTextTree(measureWithoutGrouped));
    stat.append("\n");
    if (!groupedMeasure.isEmpty()) {
      stat.append("Grouped Entries (in seconds without observation cost)\n");
      stat.append(toRankedList(groupedMeasure, measureCategories));
    }
    return stat.toString();
  }

  /**
   * Walk recursively through the measure hierarchy and assign a list of category to some measure names.
   * If a measure.name() is a key in "categoryNames", then the related value in the "categoryNames" map becomes the measure category.
   * The returned map, at the key matching the given measure.name(), will contain its measure category in addition to all
   * the measure categories of its parent. The returned map will also contains the returned map of its children.
   */
  private static Map<String, Set<String>> categorizeMeasures(DurationMeasure measure, Map<String, String> categoryNames, Set<String> parentCategories) {
    Map<String, Set<String>> categories = new HashMap<>();
    String newCategory = categoryNames.get(measure.name());
    Set<String> measureCategorize = Collections.emptySet();
    if (!parentCategories.isEmpty() || newCategory != null) {
      measureCategorize = new TreeSet<>();
      categories.put(measure.name(), measureCategorize);
      measureCategorize.addAll(parentCategories);
      if (newCategory != null) {
        measureCategorize.add(newCategory);
      }
    }
    for (DurationMeasure child : measure.children()) {
      for (Map.Entry<String, Set<String>> childEntry : categorizeMeasures(child, categoryNames, measureCategorize).entrySet()) {
        Set<String> set = categories.computeIfAbsent(childEntry.getKey(), name -> new TreeSet<>());
        set.addAll(childEntry.getValue());
        if (newCategory != null) {
          set.add(newCategory);
        }
      }
    }
    if (measureCategorize.isEmpty()) {
      categories.remove(measure.name());
    }
    return categories;
  }

  private static Map<String, DurationMeasure> extractGroupedMeasure(DurationMeasure measure, Predicate<String> groupedMeasurePredicate) {
    Map<String, DurationMeasure> groupedMap = new HashMap<>();
    measure.children().stream()
      .map(child -> extractGroupedMeasure(child, groupedMeasurePredicate))
      .forEach(childMap -> childMap.values().forEach(child -> groupedMap.merge(child.name(), child.copy(), DurationMeasure::merge)));

    DurationMeasure substitutedChild = new DurationMeasure("[ 0 grouped measure(s) ]");
    long groupedCount = 0;
    for (DurationMeasure child : new ArrayList<>(measure.children())) {
      if (groupedMeasurePredicate.test(child.name())) {
        groupedCount++;
        measure.remove(child.name());
        substitutedChild.addCalls(child.calls(), child.durationNanos());
        groupedMap.merge(child.name(), child.copy(), DurationMeasure::merge);
      }
    }
    if (groupedCount > 0) {
      substitutedChild.rename("[ " + groupedCount + " grouped measure(s) ]");
      measure.addOrMerge(substitutedChild);
    }
    return groupedMap;
  }

  public static String toTextTree(DurationMeasure measure) {
    StringBuilder out = new StringBuilder();
    toTextTreeOrderedByDuration(out, measure, "");
    return out.toString();
  }

  private static void toTextTreeOrderedByDuration(StringBuilder out, DurationMeasure measure, String indent) {
    out.append(indent).append("• ").append(toSeconds(measure.durationNanos())).append(" ").append(measure.name()).append("\n");
    measure.children().stream()
      .sorted(Comparator.comparing(x -> -x.durationNanos()))
      .forEach(child -> toTextTreeOrderedByDuration(out, child, indent + "    "));
  }

  private static String toSeconds(long durationNanos) {
    double seconds = durationNanos / 1_000_000_000.0d;
    return String.format("%13s" , TIME_FORMAT.format(seconds));
  }

  public static String toRankedList(Map<String, DurationMeasure> groupedMeasure, Map<String, Set<String>> measureCategories) {
    StringBuilder out = new StringBuilder();
    long totalDuration = groupedMeasure.values().stream().mapToLong(DurationMeasure::durationNanos).sum();
    out.append("Total   ").append(toSeconds(totalDuration)).append("\n");
    List<DurationMeasure> checkMeasures = groupedMeasure.values().stream()
      .sorted(Comparator.comparing(c -> -c.durationNanos()))
      .collect(Collectors.toList());
    for (int i = 0; i < checkMeasures.size(); i++) {
      DurationMeasure measure = checkMeasures.get(i);
      String categoryList = "";
      Set<String> categories = measureCategories.get(measure.name());
      if (categories != null) {
        categoryList = " (" + String.join(", ", categories) + ")";
      }
      out.append(toRank(i, checkMeasures))
        .append(" ").append(toSeconds(measure.durationNanos()))
        .append(" ").append(String.format("%-50s", measure.name()))
        .append(categoryList)
        .append("\n");
      measure.children().stream()
        .sorted(Comparator.comparing(x -> -x.durationNanos()))
        .forEach(child -> toTextTreeOrderedByDuration(out, child, "        "));
    }
    return out.toString();
  }

  private static String toRank(long index, Collection<?> collection) {
    return RANK_FORMAT.format(index + 1) + "/" + RANK_FORMAT.format(collection.size());
  }

}
