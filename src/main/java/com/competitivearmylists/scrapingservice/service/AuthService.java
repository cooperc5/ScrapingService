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

import com.competitivearmylists.scrapingservice.model.TokenResponse;

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
    private TokenResponse currentToken;       // holds the latest token response
    private long tokenExpiryTime = 0L;        // timestamp (ms) when the access token expires

    public AuthService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Obtains a new access token (and refresh token) from the auth server if no valid token exists.
     * If a non-expired token is already present, returns the existing token.
     *
     * @return TokenResponse containing access token, refresh token, etc.
     */
    public TokenResponse getAccessToken() {
        // Return cached token if it's still valid
        if (currentToken != null && !isTokenExpired()) {
            return currentToken;
        }
        // Prepare headers with Basic Auth (client credentials) and form content type
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        String creds = clientId + ":" + clientSecret;
        String encodedCreds = Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + encodedCreds);
        // Build form body for token request
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            body.add("grant_type", "password");
            body.add("username", username);
            body.add("password", password);
        } else {
            body.add("grant_type", "client_credentials");
        }
        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);
        // Request the access token from auth server
        ResponseEntity<TokenResponse> response =
                restTemplate.postForEntity(tokenUrl, requestEntity, TokenResponse.class);
        currentToken = response.getBody();
        if (currentToken != null) {
            // Set absolute expiration time (current time + expires_in seconds)
            tokenExpiryTime = System.currentTimeMillis() + (currentToken.getExpiresIn() * 1000);
        }
        return currentToken;
    }

    /**
     * Uses the stored refresh token (if available) to obtain a new access token from the auth server.
     *
     * @return TokenResponse with a fresh access token (and possibly a new refresh token).
     */
    public TokenResponse refreshToken() {
        if (currentToken == null || currentToken.getRefreshToken() == null) {
            throw new IllegalStateException("No refresh token available to refresh the access token.");
        }
        // Prepare headers with Basic Auth and form content type
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        String creds = clientId + ":" + clientSecret;
        String encodedCreds = Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + encodedCreds);
        // Build form body for refresh token request
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("refresh_token", currentToken.getRefreshToken());
        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);
        // Request a new token using the refresh token
        ResponseEntity<TokenResponse> response =
                restTemplate.postForEntity(tokenUrl, requestEntity, TokenResponse.class);
        TokenResponse newToken = response.getBody();
        if (newToken != null) {
            currentToken = newToken;
            tokenExpiryTime = System.currentTimeMillis() + (currentToken.getExpiresIn() * 1000);
        }
        return currentToken;
    }

    /**
     * Checks if the current access token is expired.
     *
     * @return true if no token is present or the token is expired, false otherwise.
     */
    public boolean isTokenExpired() {
        return currentToken == null || System.currentTimeMillis() >= tokenExpiryTime;
    }

    /**
     * Invalidates the current token, both locally and on the auth server (if a revocation endpoint is provided).
     * After calling this, the cached token is cleared and a new token must be fetched for further use.
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
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            // Revoke the refresh token if available; otherwise revoke the access token
            String tokenToRevoke = (currentToken.getRefreshToken() != null)
                    ? currentToken.getRefreshToken()
                    : currentToken.getAccessToken();
            body.add("token", tokenToRevoke);
            if (currentToken.getRefreshToken() != null) {
                body.add("token_type_hint", "refresh_token");
            }
            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);
            try {
                restTemplate.postForEntity(invalidateUrl, requestEntity, Void.class);
            } catch (Exception ex) {
                // Log exception if needed, but proceed to clear the token locally
            }
        }
        // Clear the cached token and reset expiration time
        currentToken = null;
        tokenExpiryTime = 0L;
    }
}
