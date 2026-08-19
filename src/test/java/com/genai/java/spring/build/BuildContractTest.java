package com.genai.java.spring.build;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BuildContractTest {

    private static final Path ROOT = Path.of("").toAbsolutePath();

    @Test
    @DisplayName("@spec:AC-015 Maven Wrapper validates both services")
    void mavenWrapperValidatesBothServices() throws IOException {
        assertTrue(Files.isRegularFile(ROOT.resolve(".mvn/wrapper/maven-wrapper.properties")));
        assertTrue(Files.isRegularFile(ROOT.resolve("mvnw")));
        assertTrue(Files.isRegularFile(ROOT.resolve("mvnw.cmd")));

        String properties = read(".mvn/wrapper/maven-wrapper.properties");
        assertTrue(Pattern.compile("(?m)^distributionUrl=https://repo\\.maven\\.apache\\.org/.+apache-maven-.+-bin\\.zip$")
                .matcher(properties)
                .find());
        assertTrue(read("mvnw").contains("maven-wrapper.properties"));
        assertTrue(read("mvnw.cmd").contains("maven-wrapper.properties"));

        String ci = read(".github/workflows/ci.yml");
        assertTrue(ci.contains("./mvnw -B -ntp test"));
        assertTrue(ci.contains("./mvnw -B -ntp -f posture-service/pom.xml test"));
    }

    @Test
    @DisplayName("@spec:AC-016 Test dependencies do not ship to production")
    void testDependenciesDoNotShipToProduction() throws IOException {
        for (String pom : new String[] {"pom.xml", "posture-service/pom.xml"}) {
            String content = read(pom);
            assertTrue(hasTestScope(content, "org.springframework.boot", "spring-boot-starter-test"), pom);
            assertTrue(hasTestScope(content, "org.testcontainers", "junit-jupiter"), pom);
            assertTrue(hasTestScope(content, "org.testcontainers", "postgresql"), pom);
        }
    }

    @Test
    @DisplayName("@spec:AC-017 @principle:P-006 CI does not consume paid APIs")
    void ciDoesNotConsumePaidApis() throws IOException {
        String ci = read(".github/workflows/ci.yml");
        assertTrue(ci.contains("OFFLINE_EVALUATION: \"true\""));
        assertFalse(Pattern.compile("secrets\\.(OPENAI|VERTEX|HUGGINGFACE|COHERE|ANTHROPIC)", Pattern.CASE_INSENSITIVE)
                .matcher(ci)
                .find());
        assertFalse(Pattern.compile("(curl|wget).*(openai|vertex|huggingface|cohere|anthropic)", Pattern.CASE_INSENSITIVE)
                .matcher(ci)
                .find());
    }

    private static boolean hasTestScope(String pom, String groupId, String artifactId) {
        Pattern dependency = Pattern.compile(
                "<dependency>(?:(?!</dependency>).)*<groupId>" + Pattern.quote(groupId)
                        + "</groupId>(?:(?!</dependency>).)*<artifactId>" + Pattern.quote(artifactId)
                        + "</artifactId>(?:(?!</dependency>).)*<scope>test</scope>(?:(?!</dependency>).)*</dependency>",
                Pattern.DOTALL);
        return dependency.matcher(pom).find();
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(ROOT.resolve(relativePath));
    }
}
