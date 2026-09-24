package edu.wylie.crs.service;

import edu.wylie.crs.dto.OfferingVO;
import edu.wylie.crs.dto.RegistrarDtos.RegistrationStatusVO;

import java.util.List;

public interface OfferingService {
    List<OfferingVO> listForStudent(String keyword, String dept);
    List<OfferingVO> listForRegistrar();
    RegistrationStatusVO status();
}
