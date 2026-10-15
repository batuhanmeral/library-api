#!/bin/sh
# Creates the two database users of the application. The PostgreSQL image runs
# this once, when it starts with an empty data directory (it is mounted into
# /docker-entrypoint-initdb.d by docker-compose.yml); an existing volume keeps the
# users it already has.
#
#   DB_OWNER_USER  owns the database and its schemas. Flyway connects with it and
#                  creates and changes the tables (spring.flyway.user).
#   DB_APP_USER    what the application connects with: it may read, add, change
#                  and delete rows, nothing else. No CREATE, ALTER, DROP or
#                  TRUNCATE, so an SQL injection could not change the schema.
#
# POSTGRES_USER, the superuser the image creates, is used for nothing but this.
set -e

: "${DB_OWNER_USER:?set DB_OWNER_USER in .env}"
: "${DB_OWNER_PASSWORD:?set DB_OWNER_PASSWORD in .env}"
: "${DB_APP_USER:?set DB_APP_USER in .env}"
: "${DB_APP_PASSWORD:?set DB_APP_PASSWORD in .env}"

fail() {
    echo "10-create-roles.sh: $1" >&2
    exit 1
}

# Three different users. A name that is already taken would make CREATE ROLE
# fail, and PostgreSQL logs a failed statement with its text, the password too.
[ "$DB_OWNER_USER" != "$POSTGRES_USER" ] || fail "DB_OWNER_USER must differ from POSTGRES_USER"
[ "$DB_APP_USER" != "$POSTGRES_USER" ] || fail "DB_APP_USER must differ from POSTGRES_USER"
[ "$DB_APP_USER" != "$DB_OWNER_USER" ] || fail "DB_APP_USER must differ from DB_OWNER_USER"
# The placeholder of .env.example is known to everybody.
[ "$DB_OWNER_PASSWORD" != "change-me" ] || fail "DB_OWNER_PASSWORD is still change-me"
[ "$DB_APP_PASSWORD" != "change-me" ] || fail "DB_APP_PASSWORD is still change-me"

# \getenv reads the values inside psql, so the passwords never appear on a
# command line (where ps would show them). :"name" quotes an identifier and
# :'name' a string literal, so no value can break out of the statement.
psql --no-psqlrc --set ON_ERROR_STOP=1 \
    --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
\getenv db POSTGRES_DB
\getenv owner DB_OWNER_USER
\getenv owner_password DB_OWNER_PASSWORD
\getenv app DB_APP_USER
\getenv app_password DB_APP_PASSWORD

CREATE ROLE :"owner" LOGIN PASSWORD :'owner_password';
CREATE ROLE :"app" LOGIN PASSWORD :'app_password';

-- The owner of a database may create schemas in it, and since PostgreSQL 15 it
-- also owns the public schema (pg_database_owner).
ALTER DATABASE :"db" OWNER TO :"owner";

-- By default every role may connect and create temporary tables; here only the
-- owner (by owning it) and the application user may connect, and nobody else
-- gets temporary tables.
REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"db" TO :"app";
-- The same for the server's own database, which neither user needs.
REVOKE ALL ON DATABASE postgres FROM PUBLIC;

-- What the owner creates later (the schemas and tables of the migrations) is
-- open to the application user for rows only. Default privileges apply to new
-- objects, so this has to be set before the first migration.
ALTER DEFAULT PRIVILEGES FOR ROLE :"owner" GRANT USAGE ON SCHEMAS TO :"app";
ALTER DEFAULT PRIVILEGES FOR ROLE :"owner"
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"app";
ALTER DEFAULT PRIVILEGES FOR ROLE :"owner" GRANT USAGE, SELECT ON SEQUENCES TO :"app";
SQL
