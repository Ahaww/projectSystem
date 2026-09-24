package edu.wylie.crs.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public class RegistrarDtos {

    public record StudentVO(
            String id,
            String name,
            String dob,
            String ssn,
            String status,
            String graduationDate
    ) {
    }

    public record StudentUpsertRequest(
            @NotBlank String name,
            String dob,
            String ssn,
            String status,
            String graduationDate
    ) {
    }

    public record ProfessorVO(
            String id,
            String name,
            String dob,
            String ssn,
            String status,
            String dept
    ) {
    }

    public record ProfessorUpsertRequest(
            @NotBlank String name,
            String dob,
            String ssn,
            String status,
            String dept
    ) {
    }

    public record RegistrationStatusVO(boolean closed, String term) {
    }

    public record CloseResultVO(List<String> cancelled, int billed, List<String> leveled) {
    }
}
