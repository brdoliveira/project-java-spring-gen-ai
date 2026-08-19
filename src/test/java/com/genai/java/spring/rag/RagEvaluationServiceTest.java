package com.genai.java.spring.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.genai.java.spring.rag.evaluation.RagEvaluationService;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RagEvaluationServiceTest {

    private static final Path GOLDEN_DATASET = Path.of("src/test/resources/rag/evaluation/golden-dataset.json");

    @Test
    @DisplayName("@spec:AC-019 Golden dataset produces deterministic retrieval metrics")
    void goldenDatasetProducesDeterministicRetrievalMetrics() throws Exception {
        RagEvaluationService service = new RagEvaluationService();

        var report = service.evaluateAndAssert(GOLDEN_DATASET, question -> switch (question) {
            case "Where is the vacation policy?" -> List.of(
                    new RagEvaluationService.RetrievedDocument("employee-handbook.pdf", 0.91));
            case "Where is the incident response process?" -> List.of(
                    new RagEvaluationService.RetrievedDocument("other.pdf", 0.95),
                    new RagEvaluationService.RetrievedDocument("security-runbook.pdf", 0.88));
            default -> List.of();
        }, 0.8);

        assertEquals(2, report.totalQuestions());
        assertEquals(1.0, report.recallAtK());
        assertEquals(0.75, report.meanReciprocalRank());
    }

    @Test
    @DisplayName("@spec:AC-019 Golden dataset fails when minimum retrieval metric is not reached")
    void goldenDatasetFailsWhenMinimumRetrievalMetricIsNotReached() {
        RagEvaluationService service = new RagEvaluationService();

        assertThrows(IllegalStateException.class,
                () -> service.evaluateAndAssert(GOLDEN_DATASET,
                        ignored -> List.of(new RagEvaluationService.RetrievedDocument("unrelated.pdf", 0.99)), 0.8));
    }
}
