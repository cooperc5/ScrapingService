package com.competitivearmylists.scrapingservice;

import com.competitivearmylists.scrapingservice.service.AuthService;
import com.competitivearmylists.scrapingservice.service.Scraper;
import com.competitivearmylists.scrapingservice.model.CompetitorEventResultDto;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Before;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;

import com.google.api.client.auth.oauth2.TokenResponse;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ScraperTests {

    @Mock
    private AuthService authService;
    @Mock
    private DataFetcher dataFetcher;  // assume DataFetcher or similar component for retrieving raw data

    @InjectMocks
    private Scraper scraper;

    // Sample HTML snippet to simulate competitor event results (for parseHtml tests and scrapeData returns)
    private static final String SAMPLE_HTML =
            "<html><body>"
                    + "<table id='results'>"
                    + "<tr><th>Event</th><th>Performance</th><th>Place</th></tr>"
                    + "<tr><td>100m</td><td>10.5 s</td><td>1</td></tr>"
                    + "<tr><td>200m</td><td>21.0 s</td><td>2</td></tr>"
                    + "</table></body></html>";

    @Before
    public void setUp() {
        // No special setup needed beyond Mockito initialization (handled by runner)
    }

    @Test
    public void testScrapeDataSuccess() throws Exception {
        // Arrange: authService returns a TokenResponse with a valid access token
        String token = "dummy-token";
        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setAccessToken(token);
        when(authService.getAccessToken()).thenReturn(tokenResponse);

        // Simulate dataFetcher returning HTML content when called with the correct token
        when(dataFetcher.fetchData(token)).thenReturn(SAMPLE_HTML);

        // Act: invoke scrapeData (should use the token and fetch data successfully)
        List<CompetitorEventResultDto> results = scraper.scrapeData();

        // Assert: verify token was obtained and used, and results were parsed correctly
        verify(authService, times(1)).getAccessToken();
        verify(dataFetcher, times(1)).fetchData(token);
        assertNotNull("Result list should not be null", results);
        assertFalse("Result list should not be empty", results.isEmpty());
        assertEquals("Expected 2 event results parsed", 2, results.size());
        // Verify the first result's fields match expected values
        CompetitorEventResultDto first = results.get(0);
        assertEquals("100m", first.getEventName());
        assertEquals("10.5 s", first.getResult());  // should include unit as per updated parsing
        assertEquals(1, first.getPosition());
        // (Additional field assertions can be added as needed)
    }

    @Test
    public void testScrapeDataRetriesOnTokenExpiry() throws Exception {
        // Arrange: Set up TokenResponse objects for first (expired) and second (refreshed) tokens
        String expiredToken = "expired-token";
        String freshToken = "fresh-token";
        TokenResponse expiredResponse = new TokenResponse();
        expiredResponse.setAccessToken(expiredToken);
        TokenResponse freshResponse = new TokenResponse();
        freshResponse.setAccessToken(freshToken);

        when(authService.getAccessToken())
                .thenReturn(expiredResponse)  // first call returns expired token
                .thenReturn(freshResponse);   // second call (after refresh) returns new token

        // Simulate first fetch attempt throwing an unauthorized exception, and second attempt succeeding
        when(dataFetcher.fetchData(expiredToken))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));
        when(dataFetcher.fetchData(freshToken))
                .thenReturn(SAMPLE_HTML);

        // Act: call scrapeData(), which should handle token expiry by retrying with a fresh token
        List<CompetitorEventResultDto> results = scraper.scrapeData();

        // Assert: authService.getAccessToken() should be called twice (initial token + refreshed token)
        verify(authService, times(2)).getAccessToken();
        // dataFetcher.fetchData should also be called twice (once failing, once succeeding)
        verify(dataFetcher, times(2)).fetchData(anyString());
        // Capture the token values used in each fetch call to ensure the refreshed token was used on retry
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(dataFetcher, times(2)).fetchData(tokenCaptor.capture());
        assertEquals("First fetch should use expired token", expiredToken, tokenCaptor.getAllValues().get(0));
        assertEquals("Second fetch should use refreshed token", freshToken, tokenCaptor.getAllValues().get(1));

        // The final result should be successfully parsed from the second attempt's data
        assertNotNull("Result list should not be null after token refresh", results);
        assertFalse("Result list should not be empty after token refresh", results.isEmpty());
        assertEquals("Expected 2 event results after retry", 2, results.size());
        // Verify parsed content is correct (e.g., first entry fields)
        CompetitorEventResultDto firstResult = results.get(0);
        assertEquals("100m", firstResult.getEventName());
        assertEquals("10.5 s", firstResult.getResult());
        assertEquals(1, firstResult.getPosition());
    }

    @Test
    public void testParseHtmlParsesEventResultsCorrectly() throws Exception {
        // Arrange: Create a Jsoup Document from the sample HTML content
        Document doc = Jsoup.parse(SAMPLE_HTML);

        // Act: Call the parseHtml method directly (now public) to parse the HTML
        List<CompetitorEventResultDto> results = scraper.parseHtml(doc);

        // Assert: The returned list should contain the expected parsed results
        assertNotNull("Parsed results should not be null", results);
        assertEquals("Expected 2 results parsed from HTML", 2, results.size());

        CompetitorEventResultDto firstResult = results.get(0);
        assertEquals("100m", firstResult.getEventName());
        // The result should include the unit (e.g., "s") as per the updated parsing logic
        assertEquals("10.5 s", firstResult.getResult());
        assertEquals(1, firstResult.getPosition());

        CompetitorEventResultDto secondResult = results.get(1);
        assertEquals("200m", secondResult.getEventName());
        assertEquals("21.0 s", secondResult.getResult());
        assertEquals(2, secondResult.getPosition());
    }
}
