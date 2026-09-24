package edu.wylie.crs.dto;

import java.util.List;

public class ProfessorDtos {

    public record UpdateTeachingRequest(List<String> offeringIds) {
    }
}
