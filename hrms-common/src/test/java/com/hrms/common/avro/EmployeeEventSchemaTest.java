package com.hrms.common.avro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hrms.common.avro.employee.EmployeeCreatedEvent;
import com.hrms.common.avro.employee.EmployeeTerminatedEvent;
import com.hrms.common.avro.employee.EmployeeUpdatedEvent;
import com.hrms.common.kafka.EmployeeEventTypes;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;
import org.junit.jupiter.api.Test;

class EmployeeEventSchemaTest {

  private static final Instant OCCURRED_AT = Instant.ofEpochMilli(1759800000000L);
  private static final Instant HIRED_AT = Instant.ofEpochMilli(1759700000000L);
  private static final Instant TERMINATED_AT = Instant.ofEpochMilli(1759900000000L);

  @Test
  void schemaNames_matchRecordNameStrategy() {
    assertEquals("com.hrms.common.avro.employee.EmployeeCreatedEvent",
        EmployeeCreatedEvent.getClassSchema().getFullName());
    assertEquals("com.hrms.common.avro.employee.EmployeeUpdatedEvent",
        EmployeeUpdatedEvent.getClassSchema().getFullName());
    assertEquals("com.hrms.common.avro.employee.EmployeeTerminatedEvent",
        EmployeeTerminatedEvent.getClassSchema().getFullName());
  }

  @Test
  void createdEvent_binaryRoundtrip() throws IOException {
    EmployeeCreatedEvent original = EmployeeCreatedEvent.newBuilder()
        .setEventId(UUID.randomUUID().toString())
        .setEventType(EmployeeEventTypes.CREATED)
        .setOccurredAt(OCCURRED_AT)
        .setEmployeeId(UUID.randomUUID().toString())
        .setEmployeeCode("NV0001")
        .setFullName("Nguyễn Văn A")
        .setEmail("a@hrms.vn")
        .setDepartmentId(UUID.randomUUID().toString())
        .setDepartmentName("Engineering")
        .setPosition("Backend Developer")
        .setStatus("ACTIVE")
        .setHiredAt(HIRED_AT)
        .setActorId(UUID.randomUUID().toString())
        .build();

    EmployeeCreatedEvent decoded =
        roundtrip(EmployeeCreatedEvent.class, original);

    assertEquals(original, decoded);
    assertEquals("NV0001", decoded.getEmployeeCode());
    assertEquals("Nguyễn Văn A", decoded.getFullName());
    assertEquals("employee.created", decoded.getEventType());
  }

  @Test
  void updatedEvent_defaultsAndNullables() {
    EmployeeUpdatedEvent event = EmployeeUpdatedEvent.newBuilder()
        .setEventId(UUID.randomUUID().toString())
        .setOccurredAt(OCCURRED_AT)
        .setEmployeeId(UUID.randomUUID().toString())
        .setEmployeeCode("NV0002")
        .setFullName("Trần Thị B")
        .setStatus("ACTIVE")
        .setChangedFields(List.of("email", "position"))
        .build();

    assertEquals("employee.updated", event.getEventType());
    assertNull(event.getEmail());
    assertNull(event.getManagerId());
    assertEquals(List.of("email", "position"), event.getChangedFields());
  }

  @Test
  void terminatedEvent_binaryRoundtrip() throws IOException {
    EmployeeTerminatedEvent original = EmployeeTerminatedEvent.newBuilder()
        .setEventId(UUID.randomUUID().toString())
        .setEventType(EmployeeEventTypes.TERMINATED)
        .setOccurredAt(OCCURRED_AT)
        .setEmployeeId(UUID.randomUUID().toString())
        .setEmployeeCode("NV0003")
        .setFullName("Lê Văn C")
        .setTerminatedAt(TERMINATED_AT)
        .setReason("Tự nguyện")
        .build();

    EmployeeTerminatedEvent decoded =
        roundtrip(EmployeeTerminatedEvent.class, original);

    assertEquals(original, decoded);
    assertEquals("employee.terminated", decoded.getEventType());
    assertTrue(decoded.getReason().length() > 0);
  }

  /**
   * Dùng constructor theo Schema (không phải theo Class) — Avro 1.12 chặn build schema từ
   * reflection (ClassSecurityValidator); path runtime Kafka cũng là schema-based nên test theo
   * đúng cách production serialize.
   */
  private static <D extends SpecificRecord> D roundtrip(Class<D> type, D record)
      throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
    new SpecificDatumWriter<D>(record.getSchema()).write(record, encoder);
    encoder.flush();

    BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(out.toByteArray(), null);
    return new SpecificDatumReader<D>(record.getSchema()).read(null, decoder);
  }
}
