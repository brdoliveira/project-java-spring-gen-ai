package com.genai.java.spring.docs;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DocumentationContractTest {

    private static final Path ROOT = Path.of("").toAbsolutePath();

    @Test
    @DisplayName("@spec:AC-020 README documents the complete execution, security, and operations path")
    void readmeDocumentsTheCompleteExecutionSecurityAndOperationsPath() throws IOException {
        String readme = Files.readString(ROOT.resolve("README.md"));

        assertContains(readme, "## Requisitos");
        assertContains(readme, "JDK 25");
        assertContains(readme, "./mvnw -B -ntp test");
        assertContains(readme, "./mvnw -B -ntp -f posture-service/pom.xml test");
        assertContains(readme, "SPRING_PROFILES_ACTIVE=local");
        assertContains(readme, "docker compose -f docker/dev/compose.yml up --build");
        assertContains(readme, "SPRING_PROFILES_ACTIVE=prod");
        assertContains(readme, "DB_PASSWORD");
        assertContains(readme, "OPENAI_API_KEY");
        assertContains(readme, "Authorization: Bearer <access-token>");
        assertContains(readme, "RagEvaluationServiceTest,RagProductionThresholdTest");

        assertTrue(Files.isRegularFile(ROOT.resolve("docker/dev/compose.yml")));
        assertTrue(Files.isRegularFile(ROOT.resolve("docker/dev/Dockerfile")));
        assertTrue(Files.isRegularFile(ROOT.resolve("docker/dev/posture-service.Dockerfile")));
        assertTrue(Files.isRegularFile(ROOT.resolve("gen-ai-with-java-spring.postman_collection.json")));
    }

    private static void assertContains(String content, String expected) {
        assertTrue(content.contains(expected), () -> "README must document: " + expected);
    }
}
