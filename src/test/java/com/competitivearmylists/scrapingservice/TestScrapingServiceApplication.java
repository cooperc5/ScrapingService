package com.competitivearmylists.scrapingservice;

import org.springframework.boot.SpringApplication;

public class TestScrapingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(ScrapingServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
