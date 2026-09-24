package edu.wylie.crs.service;

import edu.wylie.crs.dto.ScheduleDtos.ScheduleItemVO;
import edu.wylie.crs.dto.ScheduleDtos.ScheduleVO;

import java.util.List;

public interface ScheduleService {
    ScheduleVO getMine();
    ScheduleVO save(List<ScheduleItemVO> items);
    ScheduleVO submit(List<ScheduleItemVO> items);
    void deleteMine();
}
