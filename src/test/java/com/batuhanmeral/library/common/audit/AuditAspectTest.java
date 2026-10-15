package com.batuhanmeral.library.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.batuhanmeral.library.common.error.ResourceInUseException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * Audit aspect'i düz bir nesne etrafında (AspectJProxyFactory). Uygulama içindeki davranışı
 * AuditTrailTest'te.
 */
@ExtendWith(OutputCaptureExtension.class)
class AuditAspectTest {

    private final Catalog catalog = proxy(new Catalog());

    @Test
    void aSuccessfulCallIsAuditedWithItsTargetAndActor(CapturedOutput output) {
        catalog.delete(7L);

        // Satır düzeninden bağımsız olarak seviye ve logger adı.
        assertThat(output)
                .containsPattern(
                        "INFO\\s.*\\baudit\\b.*action=BOOK_DELETE target=7 actor=librarian"
                                + " outcome=SUCCESS");
    }

    @Test
    void theTargetCanComeFromTheResult(CapturedOutput output) {
        catalog.create("Dune");

        assertThat(output).contains("action=BOOK_CREATE target=42 actor=librarian outcome=SUCCESS");
    }

    @Test
    void aBrokenRuleIsAuditedWithItsErrorCodeAndStillReachesTheCaller(CapturedOutput output) {
        assertThatThrownBy(() -> catalog.deleteInUse(7L))
                .isInstanceOf(ResourceInUseException.class);

        assertThat(output)
                .contains(
                        "action=BOOK_DELETE target=7 actor=librarian outcome=FAILURE"
                                + " reason=RESOURCE_IN_USE")
                // Hata mesajı kullanıcı verisi içerebilir; audit satırında sadece kod var.
                .doesNotContain("Dune is on loan");
    }

    /** Sonuç yoksa {@code #result?.id()} okuyacak bir şey bulamaz: hedef "-". */
    @Test
    void anUnexpectedFailureIsAuditedWithTheExceptionType(CapturedOutput output) {
        assertThatThrownBy(() -> catalog.create(null)).isInstanceOf(IllegalArgumentException.class);

        assertThat(output)
                .contains(
                        "action=BOOK_CREATE target=- actor=librarian outcome=FAILURE"
                                + " reason=IllegalArgumentException");
    }

    @Test
    void anActionWithoutATargetIsAuditedWithADash(CapturedOutput output) {
        catalog.startCheck();

        assertThat(output).contains("action=OVERDUE_CHECK_START target=- actor=librarian");
    }

    /** Audit, audit ettiği çağrıyı asla bozmamalı. */
    @Test
    void aTargetExpressionThatFailsDoesNotFailTheCall(CapturedOutput output) {
        assertThat(catalog.update(7L)).isEqualTo("updated");

        assertThat(output).contains("action=BOOK_UPDATE target=? actor=librarian outcome=SUCCESS");
    }

    @Test
    void aTargetExpressionThatDoesNotParseDoesNotFailTheCall(CapturedOutput output) {
        assertThat(catalog.patch(7L)).isEqualTo("patched");

        assertThat(output).contains("action=BOOK_UPDATE target=? actor=librarian outcome=SUCCESS");
    }

    /**
     * İfadeler parametreleri okuyup metodlarını çağırabilir, fazlası değil: statik metod, tip,
     * constructor ve bean referansı yok.
     */
    @Test
    void anExpressionCannotReachTypesOrStaticMethods(CapturedOutput output) {
        catalog.readProperty();

        assertThat(output)
                .contains("action=BOOK_UPDATE target=? actor=librarian outcome=SUCCESS")
                .doesNotContain(System.getProperty("java.version"));
    }

    @Test
    void aMethodWithoutTheAnnotationIsNotAudited(CapturedOutput output) {
        catalog.find(7L);

        assertThat(output).doesNotContain("action=");
    }

    private static <T> T proxy(T target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(new AuditAspect(() -> Optional.of("librarian")));
        return factory.getProxy();
    }

    record Created(Long id) {}

    static class Catalog {

        @Auditable(action = AuditAction.BOOK_DELETE, target = "#id")
        public void delete(Long id) {}

        @Auditable(action = AuditAction.BOOK_DELETE, target = "#id")
        public void deleteInUse(Long id) {
            throw new ResourceInUseException("Dune is on loan.");
        }

        @Auditable(action = AuditAction.BOOK_CREATE, target = "#result?.id()")
        public Created create(String title) {
            if (title == null) {
                throw new IllegalArgumentException("no title");
            }
            return new Created(42L);
        }

        @Auditable(action = AuditAction.BOOK_UPDATE, target = "#unknown.id()")
        public String update(Long id) {
            return "updated";
        }

        @Auditable(action = AuditAction.BOOK_UPDATE, target = "#id +")
        public String patch(Long id) {
            return "patched";
        }

        @Auditable(
                action = AuditAction.BOOK_UPDATE,
                target = "T(java.lang.System).getProperty('java.version')")
        public void readProperty() {}

        @Auditable(action = AuditAction.OVERDUE_CHECK_START)
        public void startCheck() {}

        public String find(Long id) {
            return "found";
        }
    }
}
