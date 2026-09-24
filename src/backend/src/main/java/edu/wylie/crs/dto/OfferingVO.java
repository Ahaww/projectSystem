package edu.wylie.crs.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OfferingVO(
        String id,
        String code,
        String title,
        String dept,
        String professor,
        List<String> days,
        int start,
        int end,
        String room,
        int seatsTotal,
        int seatsTaken,
        List<String> prerequisites,
        Boolean teaching,
        Boolean cancelled
) {
}
