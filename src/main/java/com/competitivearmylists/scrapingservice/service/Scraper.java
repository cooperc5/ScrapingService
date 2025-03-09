package com.competitivearmylists.scrapingservice.service;

import com.competitivearmylists.scrapingservice.model.CompetitorEventResultDto;
import com.competitivearmylists.scrapingservice.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.http.*;
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
    private final RestTemplate restTemplate = new RestTemplate();
    // The URL or endpoint to scrape (could be injected via @Value in a config file)
    private final String targetUrl = "https://example.com/competition/results";

    /**
     * Performs the scraping of the target URL and returns a list of results.
     * Uses AuthService for authentication and includes retry logic for robustness.
     */
    public List<CompetitorEventResultDto> scrapeData() {
        log.info("Starting scrape for data from {}", targetUrl);
        String token = authService.getAccessToken();

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
                    break; // exit loop on success
                } else {
                    log.warn("Attempt {}/{}: Received non-OK HTTP status {} from target",
                            attempt, maxRetries, response.getStatusCode());
                }
            } catch (HttpClientErrorException.Unauthorized e) {
                // If unauthorized, maybe the token expired or is invalid – refresh token and retry
                log.warn("Attempt {}/{}: Received 401 Unauthorized – refreshing token and retrying", attempt, maxRetries);
                authService.invalidateToken();  // force token refresh on next call
                String newToken = authService.getAccessToken();
                headers.setBearerAuth(newToken); // update header with new token
                // continue to next loop iteration (retry with new token)
            } catch (Exception e) {
                log.error("Attempt {}/{}: Exception during HTTP fetch: {}", attempt, maxRetries, e.getMessage());
                if (attempt == maxRetries) {
                    // Give up after max retries
                    throw e;  // propagate exception after final attempt
                }
                try {
                    Thread.sleep(2000); // wait 2 seconds before retrying
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        if (htmlContent == null) {
            log.error("Failed to retrieve data from {} after {} attempts", targetUrl, maxRetries);
            return List.of(); // return empty list on failure
        }

        // Parse the HTML content using Jsoup
        List<CompetitorEventResultDto> results = parseHtml(htmlContent);
        log.info("Scraping completed. Parsed {} results from the page.", results.size());
        return results;
    }

    /**
     * Parses the HTML content to extract competitor event results.
     * This method is separated for easier testing of parsing logic.
     */
    public List<CompetitorEventResultDto> parseHtml(String htmlContent) {
        List<CompetitorEventResultDto> results = new ArrayList<>();
        Document doc = Jsoup.parse(htmlContent);

        // Example parsing logic: assume the page has a table with class "results"
        Element table = doc.selectFirst("table.results");
        if (table == null) {
            log.warn("No results table found in the HTML content.");
            return results;
        }
        Elements rows = table.select("tr");
        // Assume first row is header
        for (Element row : rows.subList(1, rows.size())) {
            Elements cells = row.select("td");
            if (cells.size() < 6) {
                // Skip if not enough columns (data might be malformed)
                log.debug("Skipping row due to unexpected number of columns: {}", row.text());
                continue;
            }
            try {
                // Extract fields in order (adjust indices based on actual HTML structure)
                String firstName = cells.get(0).text();
                String lastName = cells.get(1).text();
                String list = cells.get(2).text();
                String eventName = cells.get(3).text();
                String dateStr = cells.get(4).text();
                String result = cells.get(5).text();
                LocalDateTime eventDate = LocalDateTime.parse(dateStr); // assuming ISO date format
                CompetitorEventResultDto dto = new CompetitorEventResultDto(
                        firstName, lastName, list, eventName, eventDate, result);
                results.add(dto);
            } catch (Exception e) {
                // If parsing of a row fails, log and continue with next row
                log.error("Failed to parse row: {}. Error: {}", row.text(), e.getMessage());
            }
        }
        return results;
    }
}
