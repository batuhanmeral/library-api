package com.batuhanmeral.library.common.audit;

/**
 * The changes that are written to the audit log. The target of an action is the id of the resource
 * it changes; where that differs, the comment says what it is.
 */
public enum AuditAction {
    CATEGORY_CREATE,
    CATEGORY_UPDATE,
    CATEGORY_DELETE,
    AUTHOR_CREATE,
    AUTHOR_UPDATE,
    AUTHOR_DELETE,
    BOOK_CREATE,
    /** PUT and PATCH. */
    BOOK_UPDATE,
    BOOK_DELETE,
    /** Target: the book, so that a refused borrow names it too. */
    LOAN_BORROW,
    /** Target: the loan. */
    LOAN_RETURN,
    /** No target. */
    OVERDUE_CHECK_START,
    /** Target: the new user. The actor is "system": nobody is logged in yet. */
    USER_REGISTER,
    /**
     * No target and actor "system": the address tried is personal data and may belong to nobody.
     * Failed logins are what matters here; the request id leads to the rest of the request.
     */
    LOGIN
}
