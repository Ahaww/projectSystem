package edu.wylie.crs.repository;

import edu.wylie.crs.entity.TermConfig;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TermConfigRepository extends JpaRepository<TermConfig, String> {

    /** 关闭注册闸门：行锁，等待超时由调用方转为 409。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select t from TermConfig t where t.term = :term")
    Optional<TermConfig> lockByTerm(@Param("term") String term);
}
