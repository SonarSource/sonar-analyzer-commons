package org.sonarsource.analyzer.commons;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LengthPrefixSerializerTest {

  @Test
  public void serializing_and_unserializing_list_of_bytes_returns_same_list() throws IOException {
    byte[] bytes = new byte[]{1, 2, 3, 4, 5};
    byte[] bytes2 = new byte[]{6, 7, 8, 9, 10};
    byte[] bytes3 = new byte[]{11, 12, 13, 14, 15};

    List<byte[]> list = List.of(bytes, bytes2, bytes3);
    byte[] serialized = LengthPrefixSerializer.serializeList(list);
    List<byte[]> unserialized = LengthPrefixSerializer.unserializeList(serialized);

    assertThat(unserialized).containsExactly(bytes, bytes2, bytes3);
  }

  @Test
  public void serializing_and_unserializing_empty_list_of_bytes_returns_empty_list() throws IOException {
    List<byte[]> list = List.of();
    byte[] serialized = LengthPrefixSerializer.serializeList(list);
    List<byte[]> unserialized = LengthPrefixSerializer.unserializeList(serialized);

    assertThat(unserialized).isEmpty();
  }

  @Test
  public void serializing_and_unserializing_map_returns_same_map() throws IOException {
    Map<String, byte[]> map = Map.of("key1", new byte[]{1, 2, 3}, "key2", new byte[]{4, 5, 6});
    byte[] serialized = LengthPrefixSerializer.serializeMap(map);
    Map<String, byte[]> unserialized = LengthPrefixSerializer.unserializeMap(serialized);

    assertThat(unserialized).containsExactlyInAnyOrderEntriesOf(map);
  }

  @Test
  public void serializing_and_unserializing_empty_map_returns_empty_map() throws IOException {
    Map<String, byte[]> map = Map.of();
    byte[] serialized = LengthPrefixSerializer.serializeMap(map);
    Map<String, byte[]> unserialized = LengthPrefixSerializer.unserializeMap(serialized);

    assertThat(unserialized).isEmpty();
  }

  @Test
  public void negative_length_while_unserializing_a_list_throws_exception() throws IOException {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);
    dataOutputStream.writeInt(-1234);

    byte[] data = byteArrayOutputStream.toByteArray();

    assertThatThrownBy(() -> LengthPrefixSerializer.unserializeList(data))
      .isInstanceOf(IOException.class)
      .hasMessage("Invalid length while unserializing: -1234");
  }

  @Test
  public void negative_length_while_unserializing_a_map_key_throws_exception() throws IOException {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);
    dataOutputStream.writeInt(-1234);

    byte[] data = byteArrayOutputStream.toByteArray();

    assertThatThrownBy(() -> LengthPrefixSerializer.unserializeMap(data))
      .isInstanceOf(IOException.class)
      .hasMessage("Invalid length while unserializing: -1234");
  }

  @Test
  public void negative_length_while_unserializing_a_map_value_throws_exception() throws IOException {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);
    dataOutputStream.writeInt(3);
    dataOutputStream.write("key".getBytes());
    dataOutputStream.writeInt(-1234);

    byte[] data = byteArrayOutputStream.toByteArray();

    assertThatThrownBy(() -> LengthPrefixSerializer.unserializeMap(data))
      .isInstanceOf(IOException.class)
      .hasMessage("Invalid length while unserializing: -1234");
  }
}
