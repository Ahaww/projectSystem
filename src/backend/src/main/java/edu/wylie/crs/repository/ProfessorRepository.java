package edu.wylie.crs.repository;

import edu.wylie.crs.entity.Professor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfessorRepository extends JpaRepository<Professor, String> {
}
