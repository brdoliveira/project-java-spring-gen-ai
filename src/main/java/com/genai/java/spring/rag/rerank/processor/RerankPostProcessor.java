package com.genai.java.spring.rag.rerank.processor;

import com.genai.java.spring.config.ProviderProperties;
import com.genai.java.spring.rag.config.data.RagConfigData;
import com.genai.java.spring.rag.rerank.client.RerankerClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A {@link DocumentPostProcessor} that reorders retrieved documents using a remote
 * reranker service.
 *
 * <p>The processor queries the {@link RerankerClient} for scores for each document's
 * formatted content and sorts documents by descending score. If the reranker fails
 * the processor is fail-open and returns the original list of documents.</p>
 *
 * <p>Top-N selection is controlled by {@link RagConfigData#getRerank()}</p>
 */

@Slf4j
@Component
public class RerankPostProcessor implements DocumentPostProcessor {

    private final RerankerClient rerankerClient;
    private final RagConfigData ragConfigData;
    private final ProviderProperties providerProperties;

    public RerankPostProcessor(RerankerClient rerankerClient,
                               RagConfigData ragConfigData,
                               ProviderProperties providerProperties) {
        this.rerankerClient = rerankerClient;
        this.ragConfigData = ragConfigData;
        this.providerProperties = providerProperties;
    }

    @Override
    public List<Document> process(Query query, List<Document> documents) {
        if (documents.isEmpty() || !providerProperties.getCohere().isEnabled()) {
            return documents;
        }
        try {
            double[] scores = getScoresWithinPolicy(query, documents);
            List<Integer> indices = sortIndices(documents, scores);
            return getTopNDocuments(documents, indices);
        } catch (Exception e) {
            log.warn("Reranker unavailable; returning original documents ({})", e.getClass().getSimpleName());
            return documents;
        }
    }

    private double[] getScoresWithinPolicy(Query query, List<Document> documents) throws Exception {
        ProviderProperties.Outbound outbound = providerProperties.getOutbound();
        long deadline = System.nanoTime() + outbound.getTimeout().toNanos();
        Exception lastFailure = null;

        for (int attempt = 1; attempt <= outbound.getMaxAttempts(); attempt++) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                throw new TimeoutException("Reranker deadline exceeded");
            }

            FutureTask<double[]> call = new FutureTask<>(() -> getScores(query, documents));
            Thread worker = new Thread(call, "reranker-outbound-call");
            worker.setDaemon(true);
            worker.start();
            try {
                return call.get(remainingNanos, TimeUnit.NANOSECONDS);
            } catch (TimeoutException timeout) {
                call.cancel(true);
                throw timeout;
            } catch (ExecutionException execution) {
                call.cancel(true);
                Throwable cause = execution.getCause();
                lastFailure = cause instanceof Exception exception ? exception : execution;
            } finally {
                if (!call.isDone()) {
                    call.cancel(true);
                    worker.interrupt();
                }
            }

            if (attempt < outbound.getMaxAttempts()) {
                sleepBeforeRetry(outbound.getRetryBackoff().toNanos(), deadline);
            }
        }

        throw lastFailure == null ? new IllegalStateException("Reranker call failed") : lastFailure;
    }

    private void sleepBeforeRetry(long backoffNanos, long deadline) throws InterruptedException, TimeoutException {
        long remainingNanos = deadline - System.nanoTime();
        if (remainingNanos <= 0) {
            throw new TimeoutException("Reranker deadline exceeded");
        }
        TimeUnit.NANOSECONDS.sleep(Math.min(backoffNanos, remainingNanos));
    }

    private double[] getScores(Query query, List<Document> documents) {
        List<String> texts = documents.stream().map(Document::getFormattedContent).toList();
        return rerankerClient.score(query.text(), texts);
    }

    private List<Integer> sortIndices(List<Document> documents, double[] scores) {
        List<Integer> indices = new ArrayList<>(documents.size());
        for (int i = 0; i < documents.size(); i++) {
            indices.add(i);
        }
        indices.sort((i, j) -> Double.compare(scores[j], scores[i]));
        return indices;
    }

    private List<Document> getTopNDocuments(List<Document> documents, List<Integer> indices) {
        int topN = Math.min(ragConfigData.getRerank().getTopN(), documents.size());
        List<Document> topNDocuments = new ArrayList<>(topN);
        for (int i = 0; i < topN; i++) {
            topNDocuments.add(documents.get(indices.get(i)));
        }
        return topNDocuments;
    }
}
