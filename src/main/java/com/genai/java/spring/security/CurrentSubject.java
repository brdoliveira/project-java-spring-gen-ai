package com.genai.java.spring.security;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CurrentSubject {

    static final String LOCAL_SUBJECT = "local-demo";

    private final Environment environment;

    public CurrentSubject(Environment environment) {
        this.environment = environment;
    }

    public String require(Authentication authentication) {
        if (environment.acceptsProfiles(Profiles.of("local"))) {
            return LOCAL_SUBJECT;
        }
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return authentication.getName();
    }
}
