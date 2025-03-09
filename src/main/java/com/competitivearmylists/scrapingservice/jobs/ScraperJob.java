package com.competitivearmylists.scrapingservice.jobs;

import com.competitivearmylists.scrapingservice.service.Scraper;
import com.competitivearmylists.scrapingservice.model.CompetitorEventResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScraperJob {

    private final Scraper scraper;
    private final RestTemplate restTemplate = new RestTemplate();

    // Base URL for the StorageService (injected from configuration)
    @Value("${storage.service.url}")
    private String storageServiceBaseUrl;

    /**
     * Scheduled job that triggers the scraping and pushes data to StorageService.
     * Runs at a fixed interval defined in application properties (default to 1 hour if not set).
     */
    @Scheduled(fixedRateString = "${scraper.job.interval:3600000}")
    public void runScheduledScrape() {
        log.info("ScraperJob triggered - starting scraping process...");
        try {
            List<CompetitorEventResultDto> scrapedResults = scraper.scrapeData();
            if (scrapedResults.isEmpty()) {
                log.warn("No results scraped in this run.");
            } else {
                // Send each result to the StorageService
                for (CompetitorEventResultDto result : scrapedResults) {
                    String endpoint = storageServiceBaseUrl + "/api/v1/cer";
                    ResponseEntity<Void> response = restTemplate.postForEntity(endpoint, result, Void.class);
                    if (response.getStatusCode().is2xxSuccessful()) {
                        log.debug("Posted result for {} {} to StorageService successfully.",
                                result.getFirstName(), result.getLastName());
                    } else {
                        log.error("Failed to post result {} {} to StorageService. HTTP status: {}",
                                result.getFirstName(), result.getLastName(), response.getStatusCode());
                    }
                }
                log.info("ScraperJob: Pushed {} new results to StorageService.", scrapedResults.size());
            }
        } catch (Exception e) {
            // Catch any exception to prevent scheduler from suppressing it silently
            log.error("ScraperJob encountered an error: {}", e.getMessage(), e);
        }
    }
}
