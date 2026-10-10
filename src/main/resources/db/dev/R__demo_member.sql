-- Development data, run only by the dev profile (spring.flyway.locations): a member
-- with an overdue-check phone number and loans to look at in the database.
-- "R__" makes it repeatable: Flyway runs it again whenever the file changes, so it
-- inserts only what is missing. The password is not a BCrypt hash, so nobody can log
-- in with this account (the login answers 401); register a member of your own with
-- POST /api/v1/auth/register to borrow books.
insert into users (email, password, full_name, role, created_at, updated_at)
select 'member@example.com', '!', 'Demo Member', 'MEMBER', current_timestamp, current_timestamp
where not exists (select 1 from users where email = 'member@example.com');

-- A phone number for the SMS reminders of the overdue check (step 7). 555-0100 to
-- 555-0199 are reserved for fiction in the North American numbering plan, so it
-- belongs to nobody. A demo member created by an earlier version of this file gets
-- it here; a phone changed by hand is left alone.
update users set phone = '+15555550100'
where email = 'member@example.com' and phone is null;
