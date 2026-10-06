package com.batuhanmeral.library.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.batuhanmeral.library.category.Category;
import com.batuhanmeral.library.category.CategoryRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Learning tests: how Spring's @Transactional behaves. The bean below exists only for these tests.
 * Each test commits for real, so it cleans the categories table first.
 */
@SpringBootTest
class TransactionalBehaviourTest {

    @Autowired private Demo demo;

    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        JdbcTestUtils.deleteFromTables(
                jdbcTemplate, "loans", "book_categories", "books", "authors", "categories");
        demo.transactionNames().clear();
    }

    @Test
    void aRuntimeExceptionRollsBackTheTransaction() {
        assertThatThrownBy(() -> demo.saveThenThrowRuntime("Science"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(categoryCount()).isZero();
    }

    @Test
    void aCheckedExceptionCommitsByDefault() {
        assertThatThrownBy(() -> demo.saveThenThrowChecked("Science"))
                .isInstanceOf(Exception.class);

        // The method failed, but what it saved is in the database.
        assertThat(categoryCount()).isEqualTo(1);
    }

    @Test
    void rollbackForMakesACheckedExceptionRollBack() {
        assertThatThrownBy(() -> demo.saveThenThrowCheckedWithRollbackFor("Science"))
                .isInstanceOf(Exception.class);

        assertThat(categoryCount()).isZero();
    }

    @Test
    void aCallToThisSkipsTheProxySoRequiresNewIsIgnored() {
        demo.outerCallingInnerDirectly();

        // inner() asks for a new transaction but runs in outer()'s: the call never went
        // through the proxy that would have started one.
        assertThat(demo.transactionNames()).hasSize(2);
        assertThat(demo.transactionNames().get(1)).isEqualTo(demo.transactionNames().get(0));
    }

    @Test
    void aCallThroughTheProxyStartsTheNewTransaction() {
        demo.outerCallingInnerThroughTheProxy();

        assertThat(demo.transactionNames()).hasSize(2);
        assertThat(demo.transactionNames().get(1)).isNotEqualTo(demo.transactionNames().get(0));
        assertThat(demo.transactionNames().get(1)).endsWith("Demo.inner");
    }

    @Test
    void aRequiresNewTransactionCommitsEvenWhenTheOuterOneRollsBack() {
        assertThatThrownBy(() -> demo.saveInNewTransactionThenFail("Kept", "Lost"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(
                        jdbcTemplate.queryForList(
                                "select name from categories order by name", String.class))
                .containsExactly("Kept");
    }

    private int categoryCount() {
        return JdbcTestUtils.countRowsInTable(jdbcTemplate, "categories");
    }

    static class Demo {

        private final List<String> transactionNames = new ArrayList<>();

        private final CategoryRepository categoryRepository;

        /** This bean's own proxy. @Lazy breaks the cycle of a bean that depends on itself. */
        private final Demo self;

        Demo(CategoryRepository categoryRepository, @Lazy Demo self) {
            this.categoryRepository = categoryRepository;
            this.self = self;
        }

        @Transactional
        public void saveThenThrowRuntime(String name) {
            categoryRepository.save(new Category(name, null));
            throw new IllegalStateException("unchecked");
        }

        @Transactional
        public void saveThenThrowChecked(String name) throws Exception {
            categoryRepository.save(new Category(name, null));
            throw new Exception("checked");
        }

        @Transactional(rollbackFor = Exception.class)
        public void saveThenThrowCheckedWithRollbackFor(String name) throws Exception {
            categoryRepository.save(new Category(name, null));
            throw new Exception("checked");
        }

        @Transactional
        public void outerCallingInnerDirectly() {
            recordTransactionName();
            this.inner();
        }

        @Transactional
        public void outerCallingInnerThroughTheProxy() {
            recordTransactionName();
            self.inner();
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void inner() {
            recordTransactionName();
        }

        @Transactional
        public void saveInNewTransactionThenFail(String kept, String lost) {
            self.saveInNewTransaction(kept);
            categoryRepository.save(new Category(lost, null));
            throw new IllegalStateException("outer fails");
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void saveInNewTransaction(String name) {
            categoryRepository.save(new Category(name, null));
        }

        /**
         * Through a method, not the field: the test holds the CGLIB proxy, a subclass instance
         * whose own fields are never set. Only method calls reach the real object.
         */
        public List<String> transactionNames() {
            return transactionNames;
        }

        private void recordTransactionName() {
            transactionNames.add(TransactionSynchronizationManager.getCurrentTransactionName());
        }
    }

    @TestConfiguration
    static class DemoConfig {

        @Bean
        Demo demo(CategoryRepository categoryRepository, @Lazy Demo self) {
            return new Demo(categoryRepository, self);
        }
    }
}
