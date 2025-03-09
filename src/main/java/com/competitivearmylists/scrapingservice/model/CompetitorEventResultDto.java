package com.competitivearmylists.scrapingservice.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Data
public class CompetitorEventResultDto {
    private String firstName;
    private String lastName;
    private String emailId;
    private String result;
    private String list;
    private String eventName;
    private LocalDateTime date;

    public CompetitorEventResultDto(String firstName, String lastName, String list, String eventName, LocalDateTime eventDate, String result) {
    }
}
