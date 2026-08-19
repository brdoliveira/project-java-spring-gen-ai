package com.genai.java.spring.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.genai.java.spring.rag.evaluation.RagEvaluationService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RagProductionThresholdTest {

    @Test
    @DisplayName("@spec:AC-018 Production filters irrelevant context below a positive threshold")
    void productionFiltersIrrelevantContextBelowPositiveThreshold() {
        RagEvaluationService service = new RagEvaluationService();
        RagEvaluationService.GoldenDataset dataset = new RagEvaluationService.GoldenDataset(1.0, List.of(
                new RagEvaluationService.GoldenCase("vacation", "answer", List.of("employee-handbook.pdf"))));

        var report = service.evaluate(dataset, ignored -> List.of(
                new RagEvaluationService.RetrievedDocument("employee-handbook.pdf", 0.49),
                new RagEvaluationService.RetrievedDocument("employee-handbook.pdf", 0.81)), 0.8);

        assertEquals(1.0, report.recallAtK());
        assertThrows(IllegalArgumentException.class,
                () -> service.evaluate(dataset, ignored -> List.of(), 0.0));
    }
}
