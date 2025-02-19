package com.competitivearmylists.scrapingservice.jobs;

import com.competitivearmylists.scrapingservice.model.CompetitorEventResultDto;
import com.competitivearmylists.scrapingservice.service.Scraper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class ScraperJob {

    @Autowired
    private Scraper scraper;

    @Autowired
    private RestTemplate restTemplate;

    private static final String STORAGE_URL = "http://localhost:8080/api/results";

    // Runs daily at 6:00 AM
    @Scheduled(cron = "0 0 6 * * ?")
    public void scrapeScheduled() {
        try {
            CompetitorEventResultDto dto = scraper.scrapeData("https://example.com");
            // Post to storage-service
            restTemplate.postForObject(STORAGE_URL, dto, CompetitorEventResultDto.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
