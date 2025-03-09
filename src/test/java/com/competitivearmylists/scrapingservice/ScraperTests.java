package com.competitivearmylists.scrapingservice;

import com.competitivearmylists.scrapingservice.service.AuthService;
import com.competitivearmylists.scrapingservice.service.Scraper;
import com.competitivearmylists.scrapingservice.model.CompetitorEventResultDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScraperTests {

    @Mock
    private AuthService authService;
    @Mock
    private RestTemplate restTemplate;  // we'll inject a RestTemplate into Scraper for test
    @InjectMocks
    private Scraper scraper;

    @BeforeEach
    void setUp() {
        // If Scraper was using constructor injection for RestTemplate, we could set it here.
        // Assuming Scraper is modified to allow injecting RestTemplate (or we use reflection to set it for tests).
        // For simplicity, let's assume Scraper has a setter or package-private field we can set for RestTemplate.
        scraper.setRestTemplate(restTemplate);
    }

    @Test
    void testScrapeData_Success() {
        // Setup: AuthService returns a valid token, RestTemplate returns a sample HTML on first try
        when(authService.getAccessToken()).thenReturn("VALID_TOKEN");
        String sampleHtml = "<html><body>"
                + "<table class='results'>"
                + "<tr><th>First</th><th>Last</th><th>List</th><th>Event</th><th>Date</th><th>Result</th></tr>"
                + "<tr><td>John</td><td>Doe</td><td>ArmyList1</td><td>Winter Cup</td><td>2025-01-15</td><td>1st</td></tr>"
                + "<tr><td>Jane</td><td>Smith</td><td>ArmyList2</td><td>Winter Cup</td><td>2025-01-15</td><td>2nd</td></tr>"
                + "</table>"
                + "</body></html>";
        ResponseEntity<String> response = ResponseEntity.ok(sampleHtml);
        // Simulate restTemplate.exchange returning the sample HTML
        when(restTemplate.exchange(eq("https://example.com/competition/results"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

        List<CompetitorEventResultDto> results = scraper.scrapeData();

        // Verify AuthService was called for a token
        verify(authService, atLeastOnce()).getAccessToken();
        // Verify that we parsed two results correctly
        assertNotNull(results);
        assertEquals(2, results.size());
        // Check one of the parsed entries
        CompetitorEventResultDto firstResult = results.get(0);
        assertEquals("John", firstResult.getFirstName());
        assertEquals("Doe", firstResult.getLastName());
        assertEquals("Winter Cup", firstResult.getEventName());
        assertEquals(LocalDate.of(2025, 1, 15), firstResult.getDate());
        assertEquals("1st", firstResult.getResult());
    }

    @Test
    void testScrapeData_HandlesTokenExpiryAndRetry() {
        // Simulate token expiry scenario: first call uses an expired token causing 401, then refresh and succeed
        when(authService.getAccessToken())
                .thenReturn("EXPIRED_TOKEN")  // first call returns an expired token
                .thenReturn("NEW_TOKEN");     // second call returns a new token after refresh
        // First attempt will throw Unauthorized, second attempt will return success HTML
        when(restTemplate.exchange(eq("https://example.com/competition/results"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED))  // 401 on first try
                .thenReturn(ResponseEntity.ok("<html><body><table class='results'><tr><th>First</th><th>Last</th>"
                        + "<th>List</th><th>Event</th><th>Date</th><th>Result</th></tr>"
                        + "<tr><td>Alice</td><td>Lee</td><td>ListX</td><td>Spring Championship</td>"
                        + "<td>2025-02-20</td><td>3rd</td></tr></table></body></html>"));

        List<CompetitorEventResultDto> results = scraper.scrapeData();

        // After a 401, AuthService.getAccessToken should have been called twice (initial + after failure)
        verify(authService, times(2)).getAccessToken();
        // The final result should be parsed from the successful response
        assertEquals(1, results.size());
        assertEquals("Alice", results.get(0).getFirstName());
        assertEquals("Lee", results.get(0).getLastName());
        assertEquals("Spring Championship", results.get(0).getEventName());
        // The first token (expired) should trigger a refresh internally; ensure no lingering usage of wrong token
        Mockito.verifyNoMoreInteractions(authService);
    }

    @Test
    void testParseHtml_NoTableFound() {
        String html = "<html><body><p>No data here</p></body></html>";
        // Using protected parseHtml method directly for unit testing parsing logic
        List<CompetitorEventResultDto> results = scraper.parseHtml(html);
        assertTrue(results.isEmpty(), "Results should be empty when no table is present");
    }
}
