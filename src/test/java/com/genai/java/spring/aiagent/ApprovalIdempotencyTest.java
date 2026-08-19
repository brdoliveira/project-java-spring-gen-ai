package com.genai.java.spring.aiagent;

import com.genai.java.spring.aiagent.agent.SecurityReviewAgent;
import com.genai.java.spring.aiagent.dataaccess.helper.ReviewStateRepositoryHelper;
import com.genai.java.spring.aiagent.dto.ApprovalRequest;
import com.genai.java.spring.aiagent.dto.ApprovalType;
import com.genai.java.spring.aiagent.dto.ReviewCheckpoint;
import com.genai.java.spring.aiagent.dto.ReviewState;
import com.genai.java.spring.aiagent.dto.ReviewStatus;
import com.genai.java.spring.aiagent.service.impl.SecurityReviewServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovalIdempotencyTest {

    @Test
    @DisplayName("@spec:AC-014 Repeated approval schedules exactly one agent continuation")
    void repeatedApprovalSchedulesOnlyOneContinuation() {
        Executor executor = mock(Executor.class);
        ReviewStateRepositoryHelper repository = mock(ReviewStateRepositoryHelper.class);
        ReviewState state = ReviewState.builder()
                .id("review-1")
                .ownerSubject("alice")
                .status(ReviewStatus.PENDING_APPROVAL_DIAGRAM_EXTRACT)
                .checkpoint(ReviewCheckpoint.BEFORE_DIAGRAM_APPROVAL)
                .build();
        when(repository.loadReviewState("review-1")).thenReturn(state);
        SecurityReviewServiceImpl service = SecurityReviewAuthorizationTest.service(
                executor, mock(SecurityReviewAgent.class), repository);
        ApprovalRequest approval = new ApprovalRequest(true, null, Map.of());

        service.approveWithEdits("review-1", approval, ApprovalType.DIAGRAM_CONFIRMATION, "alice");
        assertThatThrownBy(() -> service.approveWithEdits(
                "review-1", approval, ApprovalType.DIAGRAM_CONFIRMATION, "alice"))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(state.getStatus()).isEqualTo(ReviewStatus.RUNNING);
        verify(executor, times(1)).execute(org.mockito.ArgumentMatchers.any());
    }
}
