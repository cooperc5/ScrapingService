package com.competitivearmylists.scrapingservice.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CompetitorEventResultDto {
    private String firstName;
    private String lastName;
    private String emailId;
    private String result;
    private String list;
    private String eventName;
    private LocalDateTime date;
}
