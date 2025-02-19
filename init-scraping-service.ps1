<#
.SYNOPSIS
  Initializes the file structure and stubs for the ScrapingService project.
.DESCRIPTION
  This script assumes you've already used Spring Initializr or IntelliJ
  to create the basic pom.xml and main project folder for ScrapingService.
  It then creates extra recommended packages, classes, and placeholders.
#>

param(
    [string]$BaseDir = ".",  # default to current directory
    [switch]$Force           # optional to overwrite existing files
)

Write-Host "Creating scraping-service file structure in $BaseDir"

# 1) Define directories
$mainJava       = Join-Path $BaseDir "src\main\java\com\competitivearmylists\scrapingservice"
$mainResources  = Join-Path $BaseDir "src\main\resources"
$testJava       = Join-Path $BaseDir "src\test\java\com\competitivearmylists\scrapingservice"
$configDir      = Join-Path $mainJava "config"
$controllerDir  = Join-Path $mainJava "controller"
$jobsDir        = Join-Path $mainJava "jobs"
$modelDir       = Join-Path $mainJava "model"
$serviceDir     = Join-Path $mainJava "service"

$dirsToCreate = @($mainJava, $mainResources, $testJava, $configDir, $controllerDir, $jobsDir, $modelDir, $serviceDir)

foreach ($d in $dirsToCreate) {
    if (-not (Test-Path $d)) {
        New-Item -ItemType Directory -Path $d | Out-Null
        Write-Host "Created $d"
    } else {
        Write-Host "Directory already exists: $d"
    }
}

# 2) Helper function to create/overwrite file if needed
function CreateFileIfNotExists($path, $content) {
    if (Test-Path $path) {
        # File already exists
        if (!$Force) {
            Write-Host "File already exists: $path"
            return
        }
        else {
            Write-Host "Overwriting existing file (Force): $path"
        }
    }

    $folder = Split-Path $path
    if (-not (Test-Path $folder)) {
        New-Item -ItemType Directory -Path $folder | Out-Null
    }
    Set-Content $path $content
    Write-Host "Created/overwritten file: $path"
}

# 3) Define file contents
$applicationJava = @"
package com.competitivearmylists.scrapingservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ScrapingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ScrapingServiceApplication.class, args);
    }
}
"@

$restTemplateConfig = @"
package com.competitivearmylists.scrapingservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
"@

$dtoContent = @"
package com.competitivearmylists.scrapingservice.model;

import java.time.LocalDate;

public class CompetitorEventResultDto {
    private String firstName;
    private String lastName;
    private String emailId;
    private String result;
    private String list;
    private String eventName;
    private LocalDate date;

    // Getters & Setters or use Lombok
}
"@

$scraperJava = @"
package com.competitivearmylists.scrapingservice.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import com.competitivearmylists.scrapingservice.model.CompetitorEventResultDto;

@Service
public class Scraper {

    public CompetitorEventResultDto scrapeData(String url) throws Exception {
        // Example: fetch via Jsoup
        Document doc = Jsoup.connect(url).get();

        CompetitorEventResultDto dto = new CompetitorEventResultDto();
        // parse doc.select(...) etc.
        // fill dto fields
        return dto;
    }
}
"@

$scraperJobJava = @"
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
"@

$scraperControllerJava = @"
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
            CompetitorEventResultDto dto = scraper.scrapeData(url);
            restTemplate.postForObject(STORAGE_URL, dto, CompetitorEventResultDto.class);
            return "Successfully scraped: " + url;
        } catch (Exception e) {
            e.printStackTrace();
            return "Scraping failed: " + e.getMessage();
        }
    }
}
"@

$scraperTests = @"
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
"@

# 4) Create or overwrite the relevant files
CreateFileIfNotExists (Join-Path $mainJava "ScrapingServiceApplication.java") $applicationJava
CreateFileIfNotExists (Join-Path $configDir "RestTemplateConfig.java")       $restTemplateConfig
CreateFileIfNotExists (Join-Path $modelDir "CompetitorEventResultDto.java")  $dtoContent
CreateFileIfNotExists (Join-Path $serviceDir "Scraper.java")                 $scraperJava
CreateFileIfNotExists (Join-Path $jobsDir "ScraperJob.java")                 $scraperJobJava
CreateFileIfNotExists (Join-Path $controllerDir "ScraperController.java")    $scraperControllerJava
CreateFileIfNotExists (Join-Path $testJava "ScraperTests.java")              $scraperTests

Write-Host "Initialization complete. Edit these files to suit your scraping logic."
