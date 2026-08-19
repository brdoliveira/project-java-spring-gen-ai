package com.genai.java.spring.aiagent;

import com.genai.java.spring.aiagent.agent.SecurityReviewAgent;
import com.genai.java.spring.aiagent.dataaccess.helper.ReviewStateRepositoryHelper;
import com.genai.java.spring.aiagent.dto.ApprovalRequest;
import com.genai.java.spring.aiagent.dto.ApprovalType;
import com.genai.java.spring.aiagent.dto.ReviewState;
import com.genai.java.spring.aiagent.dto.ReviewStatus;
import com.genai.java.spring.aiagent.mapper.ReviewStateMapper;
import com.genai.java.spring.aiagent.service.FileStorageService;
import com.genai.java.spring.aiagent.service.PromptSerializer;
import com.genai.java.spring.aiagent.service.impl.SecurityReviewServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityReviewAuthorizationTest {

    @Test
    @DisplayName("@spec:AC-008 A review can only be read, queried or approved by its JWT subject")
    void rejectsCrossSubjectAccessBeforeCallingTheAgent() {
        Executor executor = mock(Executor.class);
        SecurityReviewAgent agent = mock(SecurityReviewAgent.class);
        ReviewStateRepositoryHelper repository = mock(ReviewStateRepositoryHelper.class);
        when(repository.loadReviewState("review-1")).thenReturn(ReviewState.builder()
                .id("review-1")
                .ownerSubject("alice")
                .status(ReviewStatus.PENDING_APPROVAL_FINAL_REPORT)
                .build());
        SecurityReviewServiceImpl service = service(executor, agent, repository);

        assertForbidden(() -> service.getSecurityReview("review-1", "bob"));
        assertForbidden(() -> service.followUpWithVision("review-1", "secret question", "bob"));
        assertForbidden(() -> service.approveWithEdits("review-1",
                new ApprovalRequest(true, "secret note", Map.of()), ApprovalType.FINAL_REPORT_APPROVAL, "bob"));

        verify(agent, never()).followUp("review-1", "secret question");
        verify(executor, never()).execute(org.mockito.ArgumentMatchers.any());
    }

    private static void assertForbidden(Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(403));
    }

    static SecurityReviewServiceImpl service(Executor executor, SecurityReviewAgent agent,
                                             ReviewStateRepositoryHelper repository) {
        return new SecurityReviewServiceImpl(executor, mock(FileStorageService.class), agent,
                mock(ReviewStateMapper.class), repository, mock(PromptSerializer.class));
    }
}
