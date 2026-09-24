package edu.wylie.crs.repository;

import edu.wylie.crs.entity.CourseOffering;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CourseOfferingRepository extends JpaRepository<CourseOffering, String> {
    List<CourseOffering> findByTerm(String term);
    List<CourseOffering> findByProfessorId(String professorId);
    boolean existsByProfessorIdAndTerm(String professorId, String term);

    /** 条件占座：名额未满才 +1，返回更新行数（0 表示满员或班次不存在）。 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update CourseOffering c set c.seatsTaken = c.seatsTaken + 1 "
            + "where c.id = :id and c.seatsTaken < c.seatsTotal")
    int incrementSeatIfAvailable(@Param("id") String id);

    /** 释放名额：seatsTaken>0 才 -1。 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update CourseOffering c set c.seatsTaken = c.seatsTaken - 1 "
            + "where c.id = :id and c.seatsTaken > 0")
    int decrementSeat(@Param("id") String id);
}
