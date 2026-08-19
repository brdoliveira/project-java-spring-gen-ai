package com.genai.java.spring.aiagent.service;

import com.genai.java.spring.aiagent.dto.ApprovalRequest;
import com.genai.java.spring.aiagent.dto.ApprovalType;
import com.genai.java.spring.aiagent.dto.ReviewDto;
import org.springframework.web.multipart.MultipartFile;

public interface SecurityReviewService {
    String enqueueAndExecute(MultipartFile diagram, String subject);

    ReviewDto getSecurityReview(String id, String subject);

    String followUpWithVision(String id, String question, String subject);

    /**
     * Handle human approval or rejection with optional edits
     */
    void approveWithEdits(String id, ApprovalRequest approvalRequest, ApprovalType approvalType, String subject);
}
