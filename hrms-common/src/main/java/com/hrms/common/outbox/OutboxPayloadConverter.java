package com.hrms.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/** Serialize/deserialize payload outbox (JSON) — dùng khi ghi event và khi repair event lỗi. */
public final class OutboxPayloadConverter {

  private static final ObjectMapper MAPPER = JsonMapper.builder().findAndAddModules().build();

  private OutboxPayloadConverter() {}

  public static String toJson(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Không serialize được payload outbox", e);
    }
  }

  public static <T> T fromJson(String json, Class<T> type) {
    try {
      return MAPPER.readValue(json, type);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Không deserialize được payload outbox", e);
    }
  }
}
