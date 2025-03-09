package com.competitivearmylists.scrapingservice.controller;

import com.competitivearmylists.scrapingservice.model.CompetitorEventResultDto;
import com.competitivearmylists.scrapingservice.service.Scraper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ScraperController {

    // Removed hardcoded STORAGE_URL constant. Using injected configuration instead.
    @Value("${storage.url}")
    private String storageUrl; // Base URL for storage API (configured externally)

    @Autowired
    private Scraper scraper;

    @Autowired
    private RestTemplate restTemplate;

    @PostMapping("/scrapeData")
    public List<CompetitorEventResultDto> scrapeData() {
        // Call scraper to retrieve a list of competitor event results
        List<CompetitorEventResultDto> results = scraper.scrapeData(); // scrapeData now returns a list

        // Iterate through the list and post each CompetitorEventResultDto separately
        for (CompetitorEventResultDto result : results) {
            // Post each result to the storage service
            // (Previously used a hardcoded STORAGE_URL; now uses configured storageUrl)
            restTemplate.postForObject(storageUrl + "/results", result, Void.class);
        }

        // Return the list of results (could also return a status or count if needed)
        return results;
    }
}
