package edu.wylie.crs.repository;

import edu.wylie.crs.entity.IdSequence;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdSequenceRepository extends JpaRepository<IdSequence, String> {
}
