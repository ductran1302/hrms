package com.hrms.common.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hrms.common.exception.ConflictException;
import com.hrms.common.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class GlobalExceptionHandlerTest {

  private MockMvc mockMvc;

  @RestController
  static class TestController {

    @GetMapping("/conflict")
    void conflict() {
      throw new ConflictException("Mã nhân viên đã tồn tại: NV0001");
    }

    @GetMapping("/not-found")
    void notFound() {
      throw new ResourceNotFoundException("Employee", "NV0001");
    }

    @GetMapping("/validated")
    void validated(@RequestBody @Valid DemoRequest request) {
      // chỉ để kích hoạt validation
    }

    @GetMapping("/denied")
    void denied() {
      throw new AccessDeniedException("nope");
    }

    @GetMapping("/too-large")
    void tooLarge() {
      throw new MaxUploadSizeExceededException(10L * 1024 * 1024);
    }

    @GetMapping("/boom")
    void boom() {
      throw new IllegalStateException("unexpected");
    }
  }

  record DemoRequest(@NotNull String name) {}

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  @AfterEach
  void tearDown() {
    MDC.remove(CorrelationIdFilter.MDC_KEY);
  }

  @Test
  void conflict_returns409WithCodeAndMessage() throws Exception {
    mockMvc.perform(get("/conflict"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"))
        .andExpect(jsonPath("$.message").value("Mã nhân viên đã tồn tại: NV0001"))
        .andExpect(jsonPath("$.path").value("/conflict"));
  }

  @Test
  void notFound_returns404() throws Exception {
    mockMvc.perform(get("/not-found"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("Employee không tồn tại: NV0001"));
  }

  @Test
  void validation_returns400WithFieldErrors() throws Exception {
    mockMvc.perform(get("/validated")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
  }

  @Test
  void accessDenied_returns403() throws Exception {
    mockMvc.perform(get("/denied"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void uploadTooLarge_returns413() throws Exception {
    mockMvc.perform(get("/too-large"))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"))
        .andExpect(jsonPath("$.message").value("File vượt quá giới hạn cho phép"));
  }

  @Test
  void unexpectedError_returns500WithoutLeakingDetail() throws Exception {
    mockMvc.perform(get("/boom"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.message").value("Lỗi máy chủ"));
  }

  @Test
  void includesCorrelationIdFromMdc() throws Exception {
    MDC.put(CorrelationIdFilter.MDC_KEY, "corr-123");
    mockMvc.perform(get("/conflict"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.correlationId").value("corr-123"));
  }
}
