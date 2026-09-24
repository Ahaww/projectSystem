package edu.wylie.crs.repository;

import edu.wylie.crs.domain.ScheduleStatus;
import edu.wylie.crs.entity.StudentSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentScheduleRepository extends JpaRepository<StudentSchedule, Long> {
    Optional<StudentSchedule> findByStudentIdAndTerm(String studentId, String term);
    List<StudentSchedule> findByStudentId(String studentId);
    List<StudentSchedule> findByTerm(String term);
    List<StudentSchedule> findByTermAndStatusOrderBySubmitTimeAsc(String term, ScheduleStatus status);
    void deleteByStudentId(String studentId);
}
