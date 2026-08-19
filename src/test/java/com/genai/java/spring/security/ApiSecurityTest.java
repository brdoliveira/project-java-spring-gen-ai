package com.genai.java.spring.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiSecurityTest {

    @Test
    @DisplayName("@spec:AC-007 @principle:P-004 Production API requires JWT while health remains public")
    void productionApiRequiresJwtAndLeavesHealthPublic() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/genai/java/spring/security/SecurityConfiguration.java"));

        assertThat(source)
                .contains("@Profile(\"!local\")")
                .contains("/actuator/health")
                .contains(".anyRequest().authenticated()")
                .contains(".oauth2ResourceServer")
                .contains("resourceServer.jwt");

        CurrentSubject currentSubject = new CurrentSubject(new MockEnvironment());
        assertThatThrownBy(() -> currentSubject.require(null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode().value()).isEqualTo(401));
        TestingAuthenticationToken authentication = new TestingAuthenticationToken("alice", "n/a", "ROLE_USER");
        assertThat(currentSubject.require(authentication)).isEqualTo("alice");
    }

    @Test
    @DisplayName("@spec:AC-009 Explicit local profile permits the study endpoints without JWT")
    void localProfileUsesAnExplicitDemoSubject() throws Exception {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        String source = Files.readString(Path.of("src/main/java/com/genai/java/spring/security/SecurityConfiguration.java"));
        assertThat(source).contains("@Profile(\"local\")").contains(".anyRequest().permitAll()");
        assertThat(new CurrentSubject(environment).require(null)).isEqualTo("local-demo");
    }
}
