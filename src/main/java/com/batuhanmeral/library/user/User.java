package com.batuhanmeral.library.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A library user. The table is "users" because "user" is a reserved word in PostgreSQL. Users
 * register themselves, so only the timestamps are audited, not who made the change.
 *
 * <p>Only the name and the phone have setters. The address, the password hash and the role are set
 * once through the constructor: no code path, and no DTO mapped onto this entity, can change who a
 * user is or what they may do.
 */
@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    /** BCrypt hash, never the plain password. */
    @Column(nullable = false, length = 100)
    private String password;

    @Setter
    @Column(nullable = false, length = 200)
    private String fullName;

    @Setter
    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    public User(String email, String password, String fullName, Role role) {
        this.email = email;
        this.password = password;
        this.fullName = fullName;
        this.role = role;
    }

    /**
     * Email addresses are compared in lower case: "Ada@Example.com" and "ada@example.com" are one
     * account. Strictly, only the domain is case-insensitive, but no real provider tells the two
     * apart, and users do not remember how they typed it.
     */
    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
