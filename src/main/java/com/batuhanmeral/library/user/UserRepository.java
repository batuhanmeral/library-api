package com.batuhanmeral.library.user;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface UserRepository extends JpaRepository<User, Long> {

    /** E-posta önceden normalize edilmiş olmalı (User.normalizeEmail). */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Kullanıcıyı okur ve satırı transaction bitene kadar kilitler; aynı kilidi isteyen bekler.
     * Adındaki "Locked" sadece açıklamadır, Spring Data yok sayar.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findLockedById(Long id);
}
