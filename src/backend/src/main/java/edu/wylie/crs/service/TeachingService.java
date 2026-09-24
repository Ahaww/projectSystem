package edu.wylie.crs.service;

import edu.wylie.crs.dto.OfferingVO;

import java.util.List;

public interface TeachingService {
    List<OfferingVO> eligible();
    void updateTeaching(List<String> offeringIds);
}
