-- Development data, run only by the dev profile (spring.flyway.locations). There is
-- no registration yet (step 10), so this member is the one to borrow books with.
-- "R__" makes it repeatable: Flyway runs it again whenever the file changes, so it
-- inserts only what is missing. The password is not a BCrypt hash; nobody can log
-- in with this account.
insert into users (email, password, full_name, role, created_at, updated_at)
select 'member@example.com', '!', 'Demo Member', 'MEMBER', current_timestamp, current_timestamp
where not exists (select 1 from users where email = 'member@example.com');
