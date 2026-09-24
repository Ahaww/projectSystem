package edu.wylie.crs.dto;

import java.util.List;

public class GradeDtos {

    public record ReportCardItemVO(String code, String title, int credits, String grade) {
    }

    public record SemesterVO(String term, List<ReportCardItemVO> items) {
    }

    public record ReportCardVO(List<SemesterVO> semesters) {
    }

    public record TaughtOfferingVO(String id, String title, String term, int students) {
    }

    public record RosterRowVO(String studentId, String name, String grade) {
    }

    public record GradeEntry(String studentId, String grade) {
    }

    public record SaveGradesRequest(List<GradeEntry> grades) {
    }
}
