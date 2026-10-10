-- One open loan per member and book (step 12). LoanService checks this rule before
-- lending, and the member's row lock keeps two requests from both passing the check.
-- This index is the last guard: it holds for every code path, including ones that
-- forget the check, and for changes made outside the application.
--
-- A partial index: only rows that match the WHERE clause are in it, so a member may
-- have any number of returned loans of the same book. PostgreSQL only; H2, which the
-- tests used before step 12, has no partial indexes.
create unique index uk_loans_open_member_book
    on loans (member_id, book_id)
    where status <> 'RETURNED';
