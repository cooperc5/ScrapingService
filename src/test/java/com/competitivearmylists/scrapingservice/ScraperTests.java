package com.competitivearmylists.scrapingservice;

import com.competitivearmylists.scrapingservice.model.CompetitorEventResultDto;
import com.competitivearmylists.scrapingservice.service.Scraper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScraperTests {

    @Test
    void testScrapeData() throws Exception {
        Scraper scraper = new Scraper();
        CompetitorEventResultDto dto = scraper.scrapeData("https://example.com");
        // Minimal check
        assertThat(dto).isNotNull();
    }
}
