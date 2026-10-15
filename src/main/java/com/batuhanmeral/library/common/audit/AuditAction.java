package com.batuhanmeral.library.common.audit;

/**
 * Audit log'a yazılan değişiklikler. Hedef, değişen kaynağın id'sidir; farklıysa yanında yazıyor.
 */
public enum AuditAction {
    CATEGORY_CREATE,
    CATEGORY_UPDATE,
    CATEGORY_DELETE,
    AUTHOR_CREATE,
    AUTHOR_UPDATE,
    AUTHOR_DELETE,
    BOOK_CREATE,
    /** PUT ve PATCH. */
    BOOK_UPDATE,
    BOOK_DELETE,
    /** Hedef: kitap, reddedilen ödünçte de görünsün diye. */
    LOAN_BORROW,
    /** Hedef: ödünç. */
    LOAN_RETURN,
    /** Hedef yok. */
    OVERDUE_CHECK_START,
    /** Hedef: yeni kullanıcı. Aktör "system": henüz giriş yapan yok. */
    USER_REGISTER,
    /** Hedef yok, aktör "system": denenen e-posta kişisel veri ve kimseye ait olmayabilir. */
    LOGIN
}
