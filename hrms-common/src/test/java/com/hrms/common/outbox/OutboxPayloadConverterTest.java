package com.hrms.common.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OutboxPayloadConverterTest {

  record Payload(String code, int count) {}

  @Test
  void jsonRoundtrip() {
    String json = OutboxPayloadConverter.toJson(new Payload("NV0001", 42));
    Payload decoded = OutboxPayloadConverter.fromJson(json, Payload.class);
    assertEquals("NV0001", decoded.code());
    assertEquals(42, decoded.count());
  }

  @Test
  void invalidJson_throwsIllegalArgumentException() {
    org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
        () -> OutboxPayloadConverter.fromJson("{not-json", Payload.class));
  }
}
