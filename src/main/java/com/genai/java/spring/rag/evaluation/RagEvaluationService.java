package com.genai.java.spring.rag.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Runs a deterministic, offline retrieval evaluation against a versioned golden dataset.
 */
public class RagEvaluationService {

    private final ObjectMapper objectMapper;

    public RagEvaluationService() {
        this(new ObjectMapper());
    }

    public RagEvaluationService(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public RagEvaluationReport evaluate(Path goldenDataset,
                                        Function<String, List<RetrievedDocument>> retrieval,
                                        double similarityThreshold) throws IOException {
        return evaluate(objectMapper.readValue(goldenDataset.toFile(), GoldenDataset.class), retrieval, similarityThreshold);
    }

    public RagEvaluationReport evaluate(GoldenDataset goldenDataset,
                                        Function<String, List<RetrievedDocument>> retrieval,
                                        double similarityThreshold) {
        Objects.requireNonNull(goldenDataset, "goldenDataset must not be null");
        Objects.requireNonNull(retrieval, "retrieval must not be null");
        if (similarityThreshold <= 0.0) {
            throw new IllegalArgumentException("similarityThreshold must be greater than zero");
        }
        if (goldenDataset.cases() == null || goldenDataset.cases().isEmpty()) {
            throw new IllegalArgumentException("golden dataset must contain at least one case");
        }

        int matched = 0;
        double reciprocalRankSum = 0;
        for (GoldenCase evaluationCase : goldenDataset.cases()) {
            int rank = rankOfExpectedSource(retrieval.apply(evaluationCase.question()),
                    evaluationCase.expectedSources(), similarityThreshold);
            if (rank > 0) {
                matched++;
                reciprocalRankSum += 1.0 / rank;
            }
        }

        int total = goldenDataset.cases().size();
        return new RagEvaluationReport(total, matched, (double) matched / total,
                reciprocalRankSum / total, goldenDataset.minimumRecallAtK());
    }

    public RagEvaluationReport evaluateAndAssert(Path goldenDataset,
                                                 Function<String, List<RetrievedDocument>> retrieval,
                                                 double similarityThreshold) throws IOException {
        RagEvaluationReport report = evaluate(goldenDataset, retrieval, similarityThreshold);
        report.assertMinimumRecall();
        return report;
    }

    private int rankOfExpectedSource(List<RetrievedDocument> retrieved, List<String> expectedSources,
                                     double similarityThreshold) {
        if (retrieved == null || expectedSources == null) {
            return 0;
        }
        for (int index = 0; index < retrieved.size(); index++) {
            RetrievedDocument document = retrieved.get(index);
            if (document.similarityScore() >= similarityThreshold && expectedSources.contains(document.source())) {
                return index + 1;
            }
        }
        return 0;
    }

    public record GoldenDataset(double minimumRecallAtK, List<GoldenCase> cases) {
    }

    public record GoldenCase(String question, String expectedAnswer, List<String> expectedSources) {
    }

    public record RetrievedDocument(String source, double similarityScore) {
    }
}
