package com.competitivearmylists.scrapingservice.controller;

import com.competitivearmylists.scrapingservice.model.CompetitorEventResultDto;
import com.competitivearmylists.scrapingservice.service.Scraper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/scrape")
public class ScraperController {

    @Autowired
    private Scraper scraper;

    @Autowired
    private RestTemplate restTemplate;

    private static final String STORAGE_URL = "http://localhost:8080/api/results";

    @PostMapping
    public String scrapeUrl(@RequestParam String url) {
        try {
            CompetitorEventResultDto dto = scraper.scrapeData();
            restTemplate.postForObject(STORAGE_URL, dto, CompetitorEventResultDto.class);
            return "Successfully scraped: " + url;
        } catch (Exception e) {
            e.printStackTrace();
            return "Scraping failed: " + e.getMessage();
        }
    }
}
