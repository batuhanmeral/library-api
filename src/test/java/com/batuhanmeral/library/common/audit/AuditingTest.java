package com.batuhanmeral.library.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.batuhanmeral.library.category.Category;
import com.batuhanmeral.library.config.JpaAuditingConfig;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

/**
 * JPA slice test: only entities, repositories and the database are set up. The auditing
 * configuration is a regular configuration class, so the slice does not load it on its own.
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
class AuditingTest {

    @Autowired private TestEntityManager entityManager;

    @Test
    void persistFillsTheCreatedAndModifiedFields() {
        Instant before = Instant.now();

        Category category = entityManager.persistFlushFind(new Category("Science", null));

        assertThat(category.getCreatedAt()).isNotNull().isAfterOrEqualTo(before);
        assertThat(category.getUpdatedAt()).isEqualTo(category.getCreatedAt());
        assertThat(category.getCreatedBy()).isEqualTo(SystemAuditorAware.SYSTEM);
        assertThat(category.getUpdatedBy()).isEqualTo(SystemAuditorAware.SYSTEM);
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
