package com.genai.java.spring.rag.evaluation;

/**
 * Immutable result of an offline RAG retrieval evaluation.
 */
public record RagEvaluationReport(
        int totalQuestions,
        int questionsWithExpectedSource,
        double recallAtK,
        double meanReciprocalRank,
        double minimumRecallAtK) {

    public boolean meetsMinimumRecall() {
        return recallAtK >= minimumRecallAtK;
    }

    public void assertMinimumRecall() {
        if (!meetsMinimumRecall()) {
            throw new IllegalStateException("RAG recall@k %.3f is below the required minimum %.3f"
                    .formatted(recallAtK, minimumRecallAtK));
        }
    }
}
