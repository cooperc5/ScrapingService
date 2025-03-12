package com.competitivearmylists.scrapingservice.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO representing a competitor's event result.
 */
@Data
public class CompetitorEventResultDto {
    private String firstName;
    private String lastName;
    private String emailId;
    private String result;
    private String list;
    private String eventName;
    private LocalDateTime date;
    private int position;

    // No-args constructor for frameworks
    public CompetitorEventResultDto() {
    }

    public CompetitorEventResultDto(String firstName, String lastName, String list,
                                    String eventName, LocalDateTime eventDate, String result, int position) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.list = list;
        this.eventName = eventName;
        this.date = eventDate;
        this.result = result;
        this.position = position;
    }
}
