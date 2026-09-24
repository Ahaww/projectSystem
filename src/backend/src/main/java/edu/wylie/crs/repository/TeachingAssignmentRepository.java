package edu.wylie.crs.repository;

import edu.wylie.crs.entity.TeachingAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeachingAssignmentRepository extends JpaRepository<TeachingAssignment, Long> {
    List<TeachingAssignment> findByProfessorIdAndTerm(String professorId, String term);
    List<TeachingAssignment> findByOfferingIdAndTerm(String offeringId, String term);
    boolean existsByOfferingIdAndTerm(String offeringId, String term);
    boolean existsByProfessorIdAndTerm(String professorId, String term);
    void deleteByProfessorIdAndTerm(String professorId, String term);
    void deleteByProfessorId(String professorId);
}
