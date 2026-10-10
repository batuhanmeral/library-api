package com.batuhanmeral.library.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.batuhanmeral.library.DatabaseTest;
import com.batuhanmeral.library.auth.AuthenticatedUser;
import com.batuhanmeral.library.category.Category;
import com.batuhanmeral.library.config.JpaAuditingConfig;
import com.batuhanmeral.library.user.Role;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * JPA slice test: only entities, repositories and the database are set up. The auditing
 * configuration is a regular configuration class, so the slice does not load it on its own.
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
class AuditingTest implements DatabaseTest {

    @Autowired private TestEntityManager entityManager;

    @AfterEach
    void logOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void persistFillsTheCreatedAndModifiedFields() {
        Instant before = Instant.now();

        Category category = entityManager.persistFlushFind(new Category("Science", null));

        assertThat(category.getCreatedAt()).isNotNull().isAfterOrEqualTo(before);
        assertThat(category.getUpdatedAt()).isEqualTo(category.getCreatedAt());
        // Nobody is logged in: a change the application makes on its own.
        assertThat(category.getCreatedBy()).isEqualTo(SecurityAuditorAware.SYSTEM);
        assertThat(category.getUpdatedBy()).isEqualTo(SecurityAuditorAware.SYSTEM);
    }

    @Test
    void aChangeMadeInARequestWithATokenNamesItsUser() {
        // What JwtAuthenticationFilter puts into the SecurityContext for a valid token.
        SecurityContextHolder.getContext()
                .setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(
                                new AuthenticatedUser(42L, Role.ADMIN),
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        Category category = entityManager.persistFlushFind(new Category("Science", null));

        assertThat(category.getCreatedBy()).isEqualTo("user:42");
        assertThat(category.getUpdatedBy()).isEqualTo("user:42");
    }

    @Test
    void updateChangesOnlyTheModifiedTimestamp() throws InterruptedException {
        Category category = entityManager.persistFlushFind(new Category("Science", null));
        Instant createdAt = category.getCreatedAt();
        Thread.sleep(5);

        category.setDescription("Popular science");
        entityManager.flush();

        assertThat(category.getCreatedAt()).isEqualTo(createdAt);
        assertThat(category.getUpdatedAt()).isAfter(createdAt);
    }
}
