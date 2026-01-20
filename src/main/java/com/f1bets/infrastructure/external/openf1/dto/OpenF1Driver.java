package com.f1bets.infrastructure.external.openf1.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenF1Driver(
    @JsonProperty("driver_number") int driverNumber,
    @JsonProperty("full_name") String fullName,
    @JsonProperty("team_name") String teamName,
    @JsonProperty("name_acronym") String nameAcronym
) {}
