package edu.wylie.crs.repository;

import edu.wylie.crs.entity.ScheduleItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScheduleItemRepository extends JpaRepository<ScheduleItemEntity, Long> {
    List<ScheduleItemEntity> findByScheduleId(Long scheduleId);
    void deleteByScheduleId(Long scheduleId);
}
