-- Optimistic locking for books (step 6). Hibernate adds "and version = ?" to every
-- UPDATE of a book and increments the value; an UPDATE that changes no row means
-- another transaction changed the book first. Existing rows start at 0.
alter table books add column version bigint default 0 not null;
