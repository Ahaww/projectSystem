package edu.wylie.crs.service;

import edu.wylie.crs.dto.GradeDtos.ReportCardVO;
import edu.wylie.crs.dto.GradeDtos.RosterRowVO;
import edu.wylie.crs.dto.GradeDtos.SaveGradesRequest;
import edu.wylie.crs.dto.GradeDtos.TaughtOfferingVO;

import java.util.List;

public interface GradeService {
    ReportCardVO myReportCard();
    List<TaughtOfferingVO> taughtOfferings();
    List<RosterRowVO> roster(String offeringId);
    void saveGrades(String offeringId, SaveGradesRequest request);
}
