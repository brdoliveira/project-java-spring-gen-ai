package com.genai.java.spring.aiagent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveLoggingTest {

    @Test
    @DisplayName("@spec:AC-011 @principle:P-005 Questions and approval notes are not written to application logs")
    void controllerDoesNotLogSensitiveRequestBodies() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/genai/java/spring/aiagent/controller/SecurityReviewController.java"));

        assertThat(source)
                .doesNotContain("followUpRequestDto.question())")
                .doesNotContain("approvalRequest.note())")
                .doesNotContain("approvalRequest.edits())");
    }
}
