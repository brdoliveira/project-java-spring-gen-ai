package com.genai.java.spring.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

class ConfigurationSafetyTest {

    private static final Path ROOT = Path.of("").toAbsolutePath();
    private static final List<Path> RESOURCE_ROOTS = List.of(
            ROOT.resolve("src/main/resources"),
            ROOT.resolve("posture-service/src/main/resources"));

    @Test
    @DisplayName("@spec:AC-001 Startup scripts do not execute destructive SQL")
    void startupScriptsDoNotExecuteDestructiveSql() throws IOException {
        Pattern destructiveSql = Pattern.compile("(?i)\\b(?:TRUNCATE|DROP\\s+TABLE)\\b");

        for (Path resourceRoot : RESOURCE_ROOTS) {
            try (Stream<Path> files = Files.walk(resourceRoot)) {
                for (Path sql : files.filter(path -> path.toString().endsWith(".sql")).toList()) {
                    assertFalse(destructiveSql.matcher(Files.readString(sql)).find(), sql.toString());
                }
            }
        }
    }

    @Test
    @DisplayName("@spec:AC-002 RAG reindexing is opt-in by default and in production")
    void ragReindexingIsOptInByDefaultAndInProduction() throws IOException {
        assertEquals("false", property("src/main/resources/application.yml", "app.rag.force-rebuild"));
        assertEquals("false", property("src/main/resources/application-prod.yml", "app.rag.force-rebuild"));
    }

    @Test
    @DisplayName("@spec:AC-004 Defaults do not depend on a specific machine")
    void defaultsDoNotDependOnASpecificMachine() throws IOException {
        Pattern userPath = Pattern.compile("(?i)(?:/Users/[^/$\\s]+|[A-Z]:[\\\\/]Users[\\\\/][^$\\s]+)");
        Pattern literalProjectId = Pattern.compile("(?m)^\\s*project-id:\\s*(?!\\$\\{)[^#\\s]+$");
        Pattern literalPassword = Pattern.compile("(?m)^\\s*password:\\s*(?!\\$\\{)[^#\\s]+$");

        for (Path resourceRoot : RESOURCE_ROOTS) {
            try (Stream<Path> files = Files.walk(resourceRoot)) {
                for (Path yaml : files.filter(path -> path.getFileName().toString().matches("application.*\\.yml")).toList()) {
                    String content = Files.readString(yaml);
                    assertFalse(userPath.matcher(content).find(), yaml.toString());
                    assertFalse(literalProjectId.matcher(content).find(), yaml.toString());
                    assertFalse(literalPassword.matcher(content).find(), yaml.toString());
                }
            }
        }
    }

    @Test
    @DisplayName("@spec:AC-006 Production uses safe observability defaults")
    void productionUsesSafeObservabilityDefaults() throws IOException {
        String mainProd = "src/main/resources/application-prod.yml";
        String postureProd = "posture-service/src/main/resources/application-prod.yml";

        assertEquals("false", property(mainProd, "spring.ai.chat.observations.include-prompt"));
        assertEquals("false", property(mainProd, "spring.ai.chat.observations.include-completion"));
        assertEquals("OFF", property(mainProd, "logging.level.org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor"));
        assertSamplingBelowOne(mainProd);
        assertSamplingBelowOne(postureProd);
    }

    private static void assertSamplingBelowOne(String yaml) throws IOException {
        String value = property(yaml, "management.tracing.sampling.probability");
        assertTrue(Double.parseDouble(value) >= 0.0 && Double.parseDouble(value) < 1.0, yaml);
    }

    private static String property(String relativePath, String name) throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load(relativePath, new FileSystemResource(ROOT.resolve(relativePath)));
        Object value = sources.stream()
                .map(source -> source.getProperty(name))
                .filter(candidate -> candidate != null)
                .findFirst()
                .orElse(null);
        assertNotNull(value, () -> relativePath + " must define " + name);
        return String.valueOf(value);
    }
}
