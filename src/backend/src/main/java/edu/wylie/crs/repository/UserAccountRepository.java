package edu.wylie.crs.repository;

import edu.wylie.crs.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, String> {
    Optional<UserAccount> findByUsername(String username);
}
