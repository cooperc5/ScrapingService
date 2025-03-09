package com.competitivearmylists.scrapingservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class AuthService {

    @Value("${auth.tokenUrl}")
    private String tokenUrl;  // OAuth token endpoint URL

    @Value("${auth.invalidateUrl}")
    private String invalidateUrl;  // OAuth token revocation endpoint URL

    @Value("${auth.clientId}")
    private String clientId;  // OAuth client ID

    @Value("${auth.clientSecret}")
    private String clientSecret;  // OAuth client secret

    // Optional: credentials for resource owner password grant (if applicable)
    @Value("${auth.username:}")
    private String username;  // OAuth username (if using password grant)

    @Value("${auth.password:}")
    private String password;  // OAuth password (if using password grant)

    private final RestTemplate restTemplate;
    private TokenResponse currentToken;        // holds the latest token response
    private long tokenExpiryTime = 0L;         // timestamp (ms) when the access token expires

    public AuthService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Obtains a new access token (and refresh token) from the auth server if no valid token exists.
     * If a non-expired token is already present, it may return the existing token.
     * @return TokenResponse containing access token, refresh token, etc.
     */
    public TokenResponse getAccessToken() {
        // If there's a valid (non-expired) token cached, return it
        if (currentToken != null && !isTokenExpired()) {
            return currentToken;
        }
        // Prepare headers with Basic Auth (using client credentials) and form content type
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        String creds = clientId + ":" + clientSecret;
        String encodedCreds = Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + encodedCreds);

        // **Changed from HashMap to LinkedMultiValueMap for form data**
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        // Populate form parameters for token request
        if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            body.add("grant_type", "password");
            body.add("username", username);
            body.add("password", password);
        } else {
            body.add("grant_type", "client_credentials");
        }
        // (If client_id and client_secret needed in body for certain providers, they can be added here as well)

        // **Changed HttpEntity body type to MultiValueMap for FormHttpMessageConverter compatibility**
        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);
        // Request the access token from auth server
        ResponseEntity<TokenResponse> response = restTemplate.postForEntity(tokenUrl, requestEntity, TokenResponse.class);
        currentToken = response.getBody();
        if (currentToken != null) {
            // Calculate absolute expiration time (current time + expires_in seconds)
            tokenExpiryTime = System.currentTimeMillis() + (currentToken.getExpires_in() * 1000);
        }
        return currentToken;
    }

    /**
     * Uses the stored refresh token to obtain a new access token from the auth server.
     * @return TokenResponse with a fresh access token (and possibly new refresh token).
     */
    public TokenResponse refreshToken() {
        if (currentToken == null || currentToken.getRefresh_token() == null) {
            throw new IllegalStateException("No refresh token available to refresh the access token.");
        }
        // Prepare headers with Basic Auth and form content type
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        String creds = clientId + ":" + clientSecret;
        String encodedCreds = Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + encodedCreds);

        // **Changed from HashMap to LinkedMultiValueMap for form data**
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("refresh_token", currentToken.getRefresh_token());

        // **Changed HttpEntity body type to MultiValueMap for FormHttpMessageConverter compatibility**
        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);
        // Request a new token using the refresh token
        ResponseEntity<TokenResponse> response = restTemplate.postForEntity(tokenUrl, requestEntity, TokenResponse.class);
        TokenResponse newToken = response.getBody();
        if (newToken != null) {
            currentToken = newToken;
            tokenExpiryTime = System.currentTimeMillis() + (currentToken.getExpires_in() * 1000);
        }
        return currentToken;
    }

    /**
     * Checks if the current access token is expired.
     * @return true if no token is present or the token is expired, false otherwise.
     */
    public boolean isTokenExpired() {
        return currentToken == null || System.currentTimeMillis() >= tokenExpiryTime;
    }

    /**
     * Invalidates the current token, both locally and on the auth server (if a revocation endpoint is provided).
     * After calling this, any stored token is cleared and a new token must be fetched for further use.
     */
    public void invalidateToken() {
        if (currentToken == null) {
            return;  // nothing to invalidate
        }
        // If an invalidate/revoke URL is configured, call it to revoke the token on the auth server
        if (invalidateUrl != null && !invalidateUrl.isEmpty()) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            String creds = clientId + ":" + clientSecret;
            String encodedCreds = Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8));
            headers.set("Authorization", "Basic " + encodedCreds);

            // **Changed from HashMap to LinkedMultiValueMap for form data**
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            // Revoke the refresh token if available; otherwise revoke the access token
            String tokenToRevoke = currentToken.getRefresh_token() != null ? currentToken.getRefresh_token()
                    : currentToken.getAccess_token();
            body.add("token", tokenToRevoke);
            // If using OAuth2 token revocation, it's good to hint the token type being revoked
            if (currentToken.getRefresh_token() != null) {
                body.add("token_type_hint", "refresh_token");
            }

            // **Changed HttpEntity body type to MultiValueMap for FormHttpMessageConverter compatibility**
            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);
            try {
                restTemplate.postForEntity(invalidateUrl, requestEntity, Void.class);
            } catch (Exception ex) {
                // Log the exception if needed (not shown here), but still proceed to clear the token locally
            }
        }
        // Clear the cached token and reset the expiration time
        currentToken = null;
        tokenExpiryTime = 0L;
    }

    /**
     * Static inner class representing the structure of an OAuth token response.
     * (No changes made to this class)
     */
    public static class TokenResponse {
        private String access_token;
        private String refresh_token;
        private String token_type;
        private long expires_in;
        // Getters and setters for serialization/deserialization
        public String getAccess_token() { return access_token; }
        public void setAccess_token(String access_token) { this.access_token = access_token; }
        public String getRefresh_token() { return refresh_token; }
        public void setRefresh_token(String refresh_token) { this.refresh_token = refresh_token; }
        public String getToken_type() { return token_type; }
        public void setToken_type(String token_type) { this.token_type = token_type; }
        public long getExpires_in() { return expires_in; }
        public void setExpires_in(long expires_in) { this.expires_in = expires_in; }
    }
}
