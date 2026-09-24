package edu.wylie.crs.repository;

import edu.wylie.crs.entity.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {
    List<Enrollment> findByOfferingId(String offeringId);
    List<Enrollment> findByStudentId(String studentId);
    List<Enrollment> findByStudentIdAndTerm(String studentId, String term);
    Optional<Enrollment> findByOfferingIdAndStudentId(String offeringId, String studentId);
    boolean existsByOfferingIdAndStudentId(String offeringId, String studentId);
    boolean existsByStudentIdAndTerm(String studentId, String term);
    void deleteByOfferingIdAndStudentId(String offeringId, String studentId);
    void deleteByOfferingId(String offeringId);
    void deleteByStudentId(String studentId);
}
