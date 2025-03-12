package com.competitivearmylists.scrapingservice;

import com.competitivearmylists.scrapingservice.model.CompetitorEventResultDto;
import com.competitivearmylists.scrapingservice.model.TokenResponse;
import com.competitivearmylists.scrapingservice.service.AuthService;
import com.competitivearmylists.scrapingservice.service.Scraper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ScraperTests {

    @Mock
    private AuthService authService;
    @Mock
    private RestTemplate restTemplate;
    @InjectMocks
    private Scraper scraper;

    // Sample HTML snippet to simulate competitor event results
    private static final String SAMPLE_HTML =
            "<html><body>"
                    + "<table id='results'>"
                    + "<tr><th>Event</th><th>Performance</th><th>Place</th></tr>"
                    + "<tr><td>100m</td><td>10.5 s</td><td>1</td></tr>"
                    + "<tr><td>200m</td><td>21.0 s</td><td>2</td></tr>"
                    + "</table></body></html>";

    @Before
    public void setUp() {
        // No special setup needed beyond Mockito initialization
    }

    @Test
    public void testScrapeDataSuccess() throws Exception {
        // Arrange: AuthService returns a TokenResponse with a valid token
        String token = "dummy-token";
        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setAccessToken(token);
        when(authService.getAccessToken()).thenReturn(tokenResponse);
        // Simulate successful HTTP fetch of the HTML content
        ResponseEntity<String> htmlResponse = new ResponseEntity<>(SAMPLE_HTML, HttpStatus.OK);
        when(restTemplate.exchange(eq("https://example.com/competition/results"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(String.class)))
                .thenReturn(htmlResponse);

        // Act: invoke scrapeData()
        List<CompetitorEventResultDto> results = scraper.scrapeData();

        // Assert: verify token was obtained and data was fetched and parsed
        verify(authService, times(1)).getAccessToken();
        verify(restTemplate, times(1)).exchange(eq("https://example.com/competition/results"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(String.class));
        assertNotNull("Result list should not be null", results);
        assertFalse("Result list should not be empty", results.isEmpty());
        assertEquals("Expected 2 event results parsed", 2, results.size());
        CompetitorEventResultDto first = results.get(0);
        assertEquals("100m", first.getEventName());
        assertEquals("10.5 s", first.getResult());
        assertEquals(1, first.getPosition());
        // Additional field assertions (firstName, lastName, etc.) can be added if needed
    }

    @Test
    public void testScrapeDataRetriesOnTokenExpiry() throws Exception {
        // Arrange: Set up TokenResponse objects for expired and refreshed tokens
        String expiredToken = "expired-token";
        String freshToken = "fresh-token";
        TokenResponse expiredResponse = new TokenResponse();
        expiredResponse.setAccessToken(expiredToken);
        TokenResponse freshResponse = new TokenResponse();
        freshResponse.setAccessToken(freshToken);
        when(authService.getAccessToken())
                .thenReturn(expiredResponse)   // first call returns expired token
                .thenReturn(freshResponse);    // second call returns fresh token

        // Simulate first fetch throwing 401, second fetch returning HTML
        when(restTemplate.exchange(eq("https://example.com/competition/results"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED))
                .thenReturn(new ResponseEntity<>(SAMPLE_HTML, HttpStatus.OK));

        // Act: call scrapeData(), which should retry with a new token after 401
        List<CompetitorEventResultDto> results = scraper.scrapeData();

        // Assert: AuthService.getAccessToken() should be called twice (initial + refresh)
        verify(authService, times(2)).getAccessToken();
        // RestTemplate.exchange should be called twice (once failing, once succeeding)
        verify(restTemplate, times(2)).exchange(eq("https://example.com/competition/results"), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(String.class));
        // Capture the requests to verify which token was used each time
        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate, times(2)).exchange(eq("https://example.com/competition/results"), eq(HttpMethod.GET),
                requestCaptor.capture(), eq(String.class));
        List<HttpEntity> allRequests = requestCaptor.getAllValues();
        String firstAuthHeader = allRequests.get(0).getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String secondAuthHeader = allRequests.get(1).getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        assertEquals("First fetch should use expired token", "Bearer " + expiredToken, firstAuthHeader);
        assertEquals("Second fetch should use refreshed token", "Bearer " + freshToken, secondAuthHeader);
        // The final result list should be parsed from the second attempt's data
        assertNotNull("Result list should not be null after token refresh", results);
        assertFalse("Result list should not be empty after token refresh", results.isEmpty());
        assertEquals("Expected 2 event results after retry", 2, results.size());
        CompetitorEventResultDto firstResult = results.get(0);
        assertEquals("100m", firstResult.getEventName());
        assertEquals("10.5 s", firstResult.getResult());
        assertEquals(1, firstResult.getPosition());
    }

    @Test
    public void testParseHtmlParsesEventResultsCorrectly() throws Exception {
        // Arrange: create a Jsoup Document from the sample HTML content
        Document doc = Jsoup.parse(SAMPLE_HTML);

        // Act: call the parseHtml method directly with the Document
        List<CompetitorEventResultDto> results = scraper.parseHtml(doc);

        // Assert: the returned list should contain the expected parsed results
        assertNotNull("Parsed results should not be null", results);
        assertEquals("Expected 2 results parsed from HTML", 2, results.size());
        CompetitorEventResultDto firstResult = results.get(0);
        assertEquals("100m", firstResult.getEventName());
        // The result should include the unit (e.g., "s") as per the parsing logic
        assertEquals("10.5 s", firstResult.getResult());
        assertEquals(1, firstResult.getPosition());
        CompetitorEventResultDto secondResult = results.get(1);
        assertEquals("200m", secondResult.getEventName());
        assertEquals("21.0 s", secondResult.getResult());
        assertEquals(2, secondResult.getPosition());
    }
}
