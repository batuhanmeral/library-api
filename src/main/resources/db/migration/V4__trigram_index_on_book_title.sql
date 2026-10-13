-- The title search (GET /books?title=dune) is lower(title) like '%dune%'. A pattern
-- that starts with a wildcard cannot use a B-tree index, so every row is read. A
-- trigram index (pg_trgm) stores the three-letter pieces of each title and finds the
-- rows that contain all pieces of the pattern; it serves LIKE with wildcards on
-- both sides. The expression must be the same as in the query: lower(title).
--
-- pg_trgm is a trusted extension: the owner of the database may create it, no
-- superuser is needed. It costs some time on every insert and title change.
--
-- An extension exists once per database, in one schema, while these tables may be
-- in another one (docker-compose.yml runs the application in "app" and the dev
-- profile in "public" of the same database). So it always goes to public, and the
-- index names its operator class with the schema, whatever the search path is.
create extension if not exists pg_trgm with schema public;

create index ix_books_title_trgm
    on books using gin (lower(title) public.gin_trgm_ops);
