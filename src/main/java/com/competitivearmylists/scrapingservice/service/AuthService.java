package com.competitivearmylists.scrapingservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class AuthService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${auth.url}")
    private String authUrl;
    @Value("${auth.clientId}")
    private String clientId;
    @Value("${auth.clientSecret}")
    private String clientSecret;

    private String accessToken;
    private Instant tokenExpiry;  // timestamp when the token expires

    /**
     * Returns a valid access token, refreshing it if necessary.
     */
    public synchronized String getAccessToken() {
        if (accessToken == null || isTokenExpired()) {
            log.info("No valid token present or token expired. Fetching new token...");
            refreshToken();
        }
        return accessToken;
    }

    /**
     * Checks if the current token is expired or about to expire.
     */
    private boolean isTokenExpired() {
        // Consider token expired if expiry time is in the past or within 60 seconds from now
        return tokenExpiry == null || Instant.now().isAfter(tokenExpiry.minusSeconds(60));
    }

    /**
     * Calls the authentication service to get a new token using client credentials.
     */
    private void refreshToken() {
        try {
            // Prepare request body and headers for authentication (assuming form URL encoded request)
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            Map<String, String> formData = new HashMap<>();
            formData.put("grant_type", "client_credentials");
            formData.put("client_id", clientId);
            formData.put("client_secret", clientSecret);

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(formData, headers);
            ResponseEntity<TokenResponse> response = restTemplate.postForEntity(authUrl, entity, TokenResponse.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                TokenResponse tokenResponse = response.getBody();
                this.accessToken = tokenResponse.getAccessToken();
                // Set token expiry time (current time + expires_in seconds)
                this.tokenExpiry = Instant.now().plusSeconds(tokenResponse.getExpiresIn());
                log.info("Obtained new access token (expires in {} seconds)", tokenResponse.getExpiresIn());
            } else {
                String status = response.getStatusCode().toString();
                log.error("Failed to obtain access token. HTTP status: {}", status);
                throw new IllegalStateException("AuthService: Token refresh failed with status " + status);
            }
        } catch (Exception e) {
            log.error("Error during token refresh: {}", e.getMessage(), e);
            // Propagate exception to indicate auth failure (could be a custom exception in a real app)
            throw new RuntimeException("Authentication failed", e);
        }
    }

    /**
     * Simple DTO for parsing auth server responses.
     */
    static class TokenResponse {
        private String access_token;
        private long expires_in;
        // Getters and setters (or use Lombok @Data for brevity)

        public String getAccessToken() { return access_token; }
        public long getExpiresIn() { return expires_in; }
        public void setAccessToken(String token) { this.access_token = token; }
        public void setExpiresIn(long expires) { this.expires_in = expires; }
    }
}
