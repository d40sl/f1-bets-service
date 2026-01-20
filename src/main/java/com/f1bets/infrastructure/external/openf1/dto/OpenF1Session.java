package com.f1bets.infrastructure.external.openf1.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenF1Session(
    @JsonProperty("session_key") int sessionKey,
    @JsonProperty("session_name") String sessionName,
    @JsonProperty("session_type") String sessionType,
    @JsonProperty("circuit_short_name") String circuitShortName,
    @JsonProperty("country_name") String countryName,
    @JsonProperty("country_code") String countryCode,
    @JsonProperty("date_start") String dateStart,
    @JsonProperty("date_end") String dateEnd,
    @JsonProperty("year") int year
) {}
