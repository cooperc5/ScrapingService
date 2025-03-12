package com.competitivearmylists.scrapingservice.service;

import com.competitivearmylists.scrapingservice.model.CompetitorEventResultDto;
import com.competitivearmylists.scrapingservice.model.TokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class Scraper {

    private final AuthService authService;
    private final RestTemplate restTemplate;

    @Value("${scrape.targetUrl:https://example.com/competition/results}")
    private String targetUrl;

    /**
     * Performs the scraping of the target URL and returns a list of results.
     * Uses AuthService for authentication and includes retry logic for robustness.
     */
    public List<CompetitorEventResultDto> scrapeData() {
        log.info("Starting scrape for data from {}", targetUrl);
        // Obtain a valid access token
        TokenResponse tokenResponse = authService.getAccessToken();
        String token = tokenResponse.getAccessToken();
        // Prepare the request with authentication (Bearer token)
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<String> requestEntity = new HttpEntity<>(headers);

        String htmlContent = null;
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                ResponseEntity<String> response = restTemplate.exchange(
                        targetUrl, HttpMethod.GET, requestEntity, String.class);
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    htmlContent = response.getBody();
                    log.debug("Successfully fetched data on attempt {}/{}", attempt, maxRetries);
                    break;  // success: exit loop
                } else {
                    log.warn("Attempt {}/{}: Received non-OK HTTP status {} from target",
                            attempt, maxRetries, response.getStatusCode());
                }
            } catch (HttpClientErrorException.Unauthorized e) {
                // If unauthorized, the token may have expired – invalidate and refresh, then retry
                log.warn("Attempt {}/{}: Received 401 Unauthorized – refreshing token and retrying",
                        attempt, maxRetries);
                authService.invalidateToken();              // force token refresh on next call
                TokenResponse newTokenResponse = authService.getAccessToken();
                headers.setBearerAuth(newTokenResponse.getAccessToken());  // update header with new token
                // continue loop to retry with fresh token
            } catch (Exception e) {
                log.error("Attempt {}/{}: Exception during HTTP fetch: {}", attempt, maxRetries, e.getMessage());
                if (attempt == maxRetries) {
                    // Give up after max retries by rethrowing the exception
                    throw e;
                }
                try {
                    Thread.sleep(2000);  // wait 2 seconds before next retry
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        if (htmlContent == null) {
            log.error("Failed to retrieve data from {} after {} attempts", targetUrl, maxRetries);
            return List.of();  // return empty list on failure
        }

        // Parse the HTML content using Jsoup
        Document doc = Jsoup.parse(htmlContent);
        List<CompetitorEventResultDto> results = parseHtml(doc);
        log.info("Scraping completed. Parsed {} results from the page.", results.size());
        return results;
    }

    /**
     * Parses the HTML content (as a Jsoup Document) to extract competitor event results.
     * This method is separated for easier testing of parsing logic.
     */
    public List<CompetitorEventResultDto> parseHtml(Document doc) {
        List<CompetitorEventResultDto> results = new ArrayList<>();
        Element table = doc.selectFirst("table#results");
        if (table == null) {
            log.warn("No results table found in the HTML content.");
            return results;
        }
        Elements rows = table.select("tr");
        // Assume the first row is the header
        for (Element row : rows.subList(1, rows.size())) {
            Elements cells = row.select("td");
            // Expect at least 3 columns: Event, Performance, Place
            if (cells.size() < 3) {
                log.debug("Skipping row due to unexpected number of columns: {}", row.text());
                continue;
            }
            try {
                String eventName = cells.get(0).text();
                String performance = cells.get(1).text();
                String placeStr = cells.get(2).text();
                int position;
                try {
                    position = Integer.parseInt(placeStr);
                } catch (NumberFormatException nfe) {
                    position = 0;  // default to 0 if parsing fails
                }
                // Populate DTO (firstName/lastName/email/list are not provided in this HTML)
                CompetitorEventResultDto dto = new CompetitorEventResultDto();
                dto.setEventName(eventName);
                dto.setResult(performance);
                dto.setPosition(position);
                results.add(dto);
            } catch (Exception e) {
                // Log and continue if a row fails to parse
                log.error("Failed to parse row: {}. Error: {}", row.text(), e.getMessage());
            }
        }
        return results;
    }
}
