package edu.wylie.crs.repository;

import edu.wylie.crs.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, String> {
}
