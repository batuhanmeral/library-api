package com.batuhanmeral.library.user;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface UserRepository extends JpaRepository<User, Long> {

    /** The address must already be normalized (User.normalizeEmail). */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Reads the user and locks the row until the transaction ends ({@code select ... for no key
     * update} on PostgreSQL). Another transaction that asks for the same lock waits. "Locked" in
     * the name is only a description; Spring Data ignores words between "find" and "By".
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findLockedById(Long id);
}
