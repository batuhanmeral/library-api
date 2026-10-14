# library-api

A REST API for running a small library: the book catalog, its members and their loans. It is built step by step as a Spring Boot learning project.

## Features

- Administrators manage books, authors and categories.
- Members borrow and return books; anyone can browse the catalog.
- Search books by title, author, category, availability and publication year, with paging and sorting.
- Stock control: a book with no available copies cannot be borrowed, and two members cannot take the last copy at the same time.
- A member can have at most 3 active loans and cannot borrow a new book while one of their loans is overdue.
- Every night, loans past their due date are marked as overdue and the member gets a reminder.
- When a book is added with an ISBN, its missing details are filled in from Open Library.
- Every request gets an id, returned in a header and printed on every log line it causes; changes are written to an audit log.
- Sign in with JWT; administrators and members have different permissions, and members see only their own loans. Login and registration attempts are limited per client.
- Frequently read data is cached in Redis.
- Interactive API documentation with Swagger UI.
- Health checks, build info and metrics for monitoring.
- Runs as a Docker container together with PostgreSQL and Redis.

## Status

Work in progress. Available now, under `/api/v1`:

| Resource | Endpoints |
|---|---|
| Auth | `POST /auth/register`, `POST /auth/login` |
| Users | `GET /users/me`, `GET /users` |
| Categories | `GET /categories`, `GET /categories/{id}`, `POST /categories`, `PUT /categories/{id}`, `DELETE /categories/{id}` |
| Authors | `GET /authors`, `GET /authors/{id}`, `GET /authors/{id}/books`, `POST /authors`, `PUT /authors/{id}`, `DELETE /authors/{id}` |
| Books | `GET /books`, `GET /books/{id}`, `POST /books`, `PUT /books/{id}`, `PATCH /books/{id}`, `DELETE /books/{id}` |
| Loans | `POST /loans`, `POST /loans/{id}/return`, `GET /loans/me`, `GET /loans/{id}`, `GET /loans` |
| Jobs | `POST /admin/jobs/overdue-check` |

- Category names are unique, up to 100 characters; descriptions are up to 500 characters.
- A book has a title, an optional unique ISBN (10 or 13 digits), one author and any number of categories, sent as `authorId` and `categoryIds`. All copies of a new book are available; changing `totalCopies` changes `availableCopies` by the same amount.
- `POST /books` with an `isbn` fills in a missing title, description, publication year and page count from [Open Library](https://openlibrary.org); see below. The title may then be left out.
- `PUT /books/{id}` replaces the whole book; `PATCH /books/{id}` changes only the fields that are sent. A field cannot be cleared with `PATCH` (a `null` value is treated as not sent). A book can have at most 20 categories.
- An author who has books, a category that books use, or a book that has loans cannot be deleted.
- Every response has `createdAt` and `updatedAt` timestamps.

### Sign-in and permissions

Register as a member, log in, and send the token with every request:

```bash
curl -X POST http://localhost:8080/api/v1/auth/register -H 'Content-Type: application/json' \
  -d '{"email": "ada@example.com", "password": "correct horse", "fullName": "Ada Lovelace"}'
curl -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email": "ada@example.com", "password": "correct horse"}'
# {"accessToken": "eyJ...", "tokenType": "Bearer", "expiresIn": 3600}
curl http://localhost:8080/api/v1/users/me -H 'Authorization: Bearer eyJ...'
```

- Registration always creates a member. The password needs at least 8 characters and at most 72 bytes; only a BCrypt hash of it is stored. Email addresses are not case-sensitive, and an address can have one account. An optional `phone` in international form (`+905551234567`) receives the overdue reminders.
- A token is valid for one hour; then log in again. There is no logout: a token stays valid until it expires.
- The administrator account is created at startup from `ADMIN_EMAIL` and `ADMIN_PASSWORD` (see Run). An existing administrator with that address is never changed; if the address belongs to a member, the application does not start.
- Failed logins are written to the audit log (`action=LOGIN ... outcome=FAILURE`), without the address that was tried.
- One client may send at most 10 login requests a minute and 10 registrations an hour, right or wrong; then the answer is `429 TOO_MANY_REQUESTS` with a `Retry-After` header (seconds until the window ends) and the password is not checked. A client is one IPv4 address or one IPv6 /64 network. The counters are kept in Redis; while Redis is down, requests are let through without a limit. Behind a reverse proxy, set `server.forward-headers-strategy=native` so that the client address comes from the proxy's `X-Forwarded-For` header (only then: without a proxy a client could send any address in it).

| Who | May |
|---|---|
| Anyone | read categories, authors and books; register; log in |
| Member | everything above; borrow books; see and return their own loans (`GET /loans/me`); `GET /users/me` |
| Administrator | everything above except borrowing; create, change and delete categories, authors and books; see and return any loan; list all loans and users; start the overdue check |

Without a token where one is needed the answer is `401 AUTHENTICATION_REQUIRED`; with a malformed, forged or expired token it is `401 INVALID_TOKEN`, also on endpoints that need none; with a token whose user may not do it, `403 ACCESS_DENIED`. Paths that are not part of the API are refused the same way.

### Paging, sorting and search

`GET /authors`, `GET /books` and `GET /authors/{id}/books` return one page:

```json
{ "content": [ ... ], "page": 0, "size": 20, "totalElements": 42, "totalPages": 3 }
```

- `page` starts at 0; `size` defaults to 20 and is capped at 100.
- `sort=field` or `sort=field,desc`, repeatable. Books can be sorted by `id`, `title`, `publishedYear`, `availableCopies` and `createdAt` (default `title`); authors by `id`, `name`, `birthDate` and `createdAt` (default `name`).
- `GET /books` filters, all optional and combined with AND:

| Parameter | Matches books |
|---|---|
| `title` | whose title contains the text, ignoring case |
| `authorId` | by that author |
| `categoryId` | in that category |
| `available` | `true`: with at least one copy available; `false`: with none |
| `yearFrom`, `yearTo` | published in that range, inclusive |

```bash
curl 'http://localhost:8080/api/v1/books?title=hobbit&available=true&sort=publishedYear,desc&size=10'
```

The category list is not paged.

### Adding a book by ISBN

```bash
curl -i -X POST http://localhost:8080/api/v1/books \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"isbn": "9780441172719", "totalCopies": 2, "authorId": 1}'
```

The response has the title, year and page count Open Library knows for that edition. The rules:

- Values sent in the request always win; Open Library only fills in what is missing. It is not asked when nothing is missing, when there is no ISBN, or when the ISBN is already taken or the author does not exist.
- Without an ISBN, the title is required (400).
- Without a title: an ISBN Open Library does not know is `422 ISBN_NOT_FOUND`; if Open Library cannot be reached or does not answer in time, `503 EXTERNAL_SERVICE_UNAVAILABLE`.
- With a title, a failed lookup does not stop the book from being saved with the values that were sent; the log shows a warning.
- Data from Open Library is checked before it is saved: texts are cut to the field lengths and cleaned of control characters, an implausible year or page count is dropped.
- `PUT` and `PATCH` never ask Open Library.

This needs internet access to `openlibrary.org`. The tests never use the network.

### Loans

Borrow a copy as a member with `POST /loans` and `{"bookId": 1}`; the borrower is the user of the token, and the loan is due 14 days later. Return it with `POST /loans/{id}/return`. `GET /loans/me` lists the member's own loans, `GET /loans` (administrators) everybody's; both are paged (newest first), take an optional `status` filter (`ACTIVE`, `OVERDUE`, `RETURNED`) and can be sorted by `id`, `loanDate`, `dueDate`, `returnDate` and `createdAt`. `GET /loans` also filters by `memberId`.

- A member can see and return only their own loans (`403 ACCESS_DENIED` for another member's); an administrator can see and return any loan.

- Only members can borrow; an administrator cannot.
- A book with no available copy cannot be borrowed. When several members borrow the same book at the same moment, the requests are retried as needed: each gets a copy while copies last, the others get `BOOK_NOT_AVAILABLE`.
- A member cannot borrow a second copy of a book they already have on loan.
- A member can have at most 3 open (active or overdue) loans.
- A member with a loan past its due date cannot borrow until it is returned.
- `totalCopies` of a book cannot be set below the number of copies on loan.

With the `dev` profile, a demo member (`member@example.com`, phone `+1 555 555 0100`, a number reserved for fiction) is created at startup for the overdue reminders. It has no usable password; register your own member to borrow books.

### Caching

`GET /books/{id}` and `GET /categories` are cached in Redis. A change made through the API (updating or deleting a book, borrowing or returning it, renaming its author or a category, any change of a category) removes the affected entries once it is saved, so the next read gets the new data. Entries also expire after 10 minutes (books) and 1 hour (the category list). If Redis is down, requests are still answered from the database; the log shows a warning for each failed cache access.

Measured on a laptop against the local PostgreSQL and Redis containers, 100 requests each after a warm-up: `GET /books/{id}` took 1.9 ms on average with the cache and 4.0 ms without it (95th percentile 2.7 ms and 4.6 ms); the list of 50 categories took 1.3 ms and 2.6 ms (95th percentile 1.9 ms and 3.8 ms).

### Notifications and the nightly overdue check

- After a loan or a return is saved, the member gets a confirmation email.
- Every night at 02:00 (Europe/Istanbul), active loans past their due date are marked `OVERDUE` and each member concerned gets one SMS reminder, if they have a phone number. Running the check again changes nothing.
- `POST /admin/jobs/overdue-check` starts the same check at once and returns `202 Accepted` without waiting for it; while a run is going, another start gets `409 JOB_ALREADY_RUNNING`. Only administrators can call it.

Emails and SMS are not really sent; they are written to the application log, with the address shortened to its first letter and domain (`m***@example.com`) and the phone number to its last two digits (`***00`).

### Request ids and logs

- Every response has an `X-Request-Id` header. A client can send its own id (letters, digits, `.`, `_` and `-`, up to 64 characters), for example to follow a request through a gateway; any other value is replaced by a new id. Quote the id when reporting a problem.
- Every log line written while serving a request shows its id in brackets, also the lines of work the request started in the background (the overdue check). The nightly run, which no request started, has an id of its own (`overdue-check-1a2b3c4d`):

  ```text
  ... [postman-request-1] c.b.l.c.l.RequestLoggingInterceptor : GET /api/v1/categories returned 200 in 4 ms (CategoryController.findAll)
  ```

- Each API call is logged with its method, path, status, duration and the controller method that served it. Query strings are not logged.
- Changes are written to the `audit` logger, one line each: creating, updating and deleting categories, authors and books, borrowing and returning, starting the overdue check, registering. A line has the action, the id of what was changed, who made the change (`user:<id>` of the token, or `system` for changes without one) and the outcome; a refused change names its error code:

  ```text
  action=AUTHOR_DELETE target=3 actor=user:1 outcome=FAILURE reason=RESOURCE_IN_USE
  ```

- The `createdBy` and `updatedBy` columns of catalogue and loan rows record the same `user:<id>`.

- A service call that takes 500 ms or more is logged as a warning. With the `dev` profile the duration of every service call is logged.
- With the `prod` profile, log lines are JSON objects in [Elastic Common Schema](https://www.elastic.co/guide/en/ecs/current/index.html); the request id is the `requestId` field.

### API documentation

Swagger UI at `http://localhost:8080/swagger-ui.html` lists every endpoint with its parameters, bodies and responses, and can send requests. Log in with `POST /api/v1/auth/login`, press "Authorize" and paste the `accessToken`; requests that need a token then send it. The OpenAPI description itself is at `/v3/api-docs`. With the `prod` profile both are off unless `API_DOCS_ENABLED=true`.

### Monitoring

| Endpoint | Who | What |
|---|---|---|
| `GET /actuator/health` | anyone | `UP` or `DOWN`; an administrator also sees each part (database, Redis, disk space) |
| `GET /actuator/health/liveness` | anyone | the process is running; the Docker health check asks this |
| `GET /actuator/health/readiness` | anyone | ready to serve requests: the database answers. Redis is not part of it; without the cache requests are only slower |
| `GET /actuator/info` | anyone | the build: group, artifact, version |
| `GET /actuator/metrics`, `GET /actuator/metrics/{name}` | administrators | request counts and times (`http.server.requests`), JVM, connection pool, cache hits and misses (`cache.gets`), loans (`library.loans.borrowed`, `library.loans.returned`) |

Filter a metric by tags, for example cache hits of the book cache: `/actuator/metrics/cache.gets?tag=cache:books&tag=result:hit`. Other Actuator endpoints are not exposed.

### Settings

```yaml
library:
  time-zone: Europe/Istanbul      # "today" for loans and the time of the nightly check
  scheduling:
    enabled: true                 # false: the nightly check does not run by itself
  loan:
    max-active-loans: 3
    period-days: 14
    overdue-check:
      cron: "0 0 2 * * *"         # second minute hour day month weekday
      batch-size: 100             # members handled per database transaction
  open-library:
    base-url: https://openlibrary.org
    connect-timeout: 2s           # both timeouts are required, at most 30s
    read-timeout: 3s
    user-agent: "library-api (Spring Boot learning project)"
  logging:
    slow-call-threshold: 500ms    # service calls at least this long are logged as warnings
  cache:
    books-ttl: 10m                # how long a cached book lives
    categories-ttl: 1h            # how long the cached category list lives
  security:
    rate-limit:
      enabled: true               # false: no limit on logins and registrations
      login:
        limit: 10                 # requests per client and window
        window: 1m
      register:
        limit: 10
        window: 1h
    jwt:
      expiration: 1h              # how long a token is accepted
      issuer: library-api
```

The token signing key (`JWT_SECRET`) and the administrator account (`ADMIN_EMAIL`, `ADMIN_PASSWORD`) come from the environment; see Run.

They can be overridden like any Spring Boot property, for example with `LIBRARY_LOAN_MAX_ACTIVE_LOANS=5`. The application does not start if a value is missing or invalid (a limit below 1, an unknown time zone).

Data is stored in PostgreSQL; Redis holds the cache and the login and registration counters. Every Redis key starts with `library-api:`. The `dev` and `prod` profiles cannot be active together: the application refuses to start, since `dev` adds a demo member to the database.

## Errors

Errors are returned as [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details (`application/problem+json`) with an extra `errorCode` field to branch on:

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "The request has invalid fields.",
  "instance": "/api/v1/categories",
  "errorCode": "VALIDATION_FAILED",
  "errors": [{ "field": "name", "message": "must not be blank" }]
}
```

| errorCode | Status | When |
|---|---|---|
| `VALIDATION_FAILED` | 400 | A body field or a search filter is missing or invalid; `errors` lists each field |
| `MALFORMED_REQUEST` | 400 | The body is not valid JSON, a path value has the wrong type, or a text value cannot be stored (a NUL character) |
| `INVALID_PARAMETER` | 400 | A query parameter cannot be used: a sort field that is not allowed, or `yearFrom` after `yearTo` |
| `AUTHENTICATION_REQUIRED` | 401 | No token where one is needed |
| `INVALID_TOKEN` | 401 | The token is malformed, signed with another key or expired |
| `INVALID_CREDENTIALS` | 401 | Login with a wrong password or an unknown email address |
| `ACCESS_DENIED` | 403 | The user of the token may not do this, for example a member changing a book or reading another member's loan |
| `RESOURCE_NOT_FOUND` | 404 | No resource with that id (also an `authorId` or `categoryIds` entry in a book), or no such path |
| `METHOD_NOT_ALLOWED` | 405 | The path does not support the HTTP method |
| `DUPLICATE_RESOURCE` | 409 | The name, ISBN or email address is already taken |
| `RESOURCE_IN_USE` | 409 | The author has books, books use the category, or the book has loans |
| `DATA_CONFLICT` | 409 | The change conflicts with existing data |
| `CONCURRENT_UPDATE` | 409 | Another request changed the same data at the same time, even after retries; reload and try again |
| `BOOK_NOT_AVAILABLE` | 409 | The book has no copy left to borrow |
| `COPIES_ON_LOAN` | 409 | `totalCopies` would be lower than the copies on loan |
| `LOAN_ALREADY_RETURNED` | 409 | The loan was already returned |
| `BOOK_ALREADY_BORROWED` | 409 | The member already has this book on loan |
| `JOB_ALREADY_RUNNING` | 409 | The overdue check is already running |
| `LOAN_LIMIT_REACHED` | 422 | The member already has the most open loans allowed |
| `MEMBER_HAS_OVERDUE_LOAN` | 422 | The member has a loan past its due date |
| `NOT_A_MEMBER` | 422 | The user of the token is no longer a member (the role changed after the token was issued) |
| `ISBN_NOT_FOUND` | 422 | A new book has no title and Open Library does not know its ISBN |
| `TOO_MANY_REQUESTS` | 429 | Too many logins or registrations from one client; `Retry-After` says how many seconds to wait |
| `EXTERNAL_SERVICE_UNAVAILABLE` | 503 | A new book has no title and Open Library could not be reached; send the title or try again later |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | The body is not `application/json` |
| `REQUEST_REJECTED` | 400 | The request was refused before it reached the API: a `;` or an invalid encoding in the path, for example. Also any other client error (4xx) |
| `INTERNAL_ERROR` | 500 | Unexpected server error |

## Requirements

- JDK 21 or newer. Maven is not needed; the Maven Wrapper (`./mvnw`) downloads it.
- Docker with Docker Compose, for PostgreSQL and Redis. The tests need Docker too: they start their own PostgreSQL and Redis containers.

## Run

Copy the example settings and fill in `.env`: passwords for the three database users, a Redis password, a token signing key (`JWT_SECRET`, the output of `openssl rand -base64 32`; the application does not start with the placeholder) and the administrator's email and password. Then start PostgreSQL and the application:

```bash
cp .env.example .env
docker compose up -d --wait
./mvnw spring-boot:run
```

`./mvnw spring-boot:run` uses the `dev` profile: it connects to the database from `docker-compose.yml` on `localhost:5434` (`POSTGRES_PORT` in `.env`; change it if the port is busy) and to Redis on `localhost:6380` (`REDIS_PORT`), and logs SQL statements. The schema is created and updated by Flyway at startup. `docker compose down` stops the database and keeps the data; `docker compose down -v` deletes it too.

The database has three users, all from `.env`. `POSTGRES_USER` is the superuser of the container and only creates the other two on the first start. `DB_OWNER_USER` owns the tables: the migrations run as this user. `DB_APP_USER` is what the application connects with; it can read, add, change and delete rows, but cannot create, change, empty or drop tables. The two users are created only when the database volume is new: after updating from a version without them, add the `DB_OWNER_*` and `DB_APP_*` lines from `.env.example` to `.env` and recreate the volume with `docker compose down -v` (this deletes the local data).

To run the packaged application elsewhere, use the `prod` profile and pass the connections and the signing key as environment variables (`REDIS_PORT` defaults to 6379). `DB_USERNAME` is the application's user and `DB_MIGRATION_USERNAME` the owner of the tables, which runs the migrations at startup; they may be the same user. Set `ADMIN_EMAIL` and `ADMIN_PASSWORD` too on the first start to create the administrator:

```bash
./mvnw package
SPRING_PROFILES_ACTIVE=prod DB_URL=jdbc:postgresql://db-host:5432/library \
  DB_USERNAME=library_app DB_PASSWORD=... \
  DB_MIGRATION_USERNAME=library_owner DB_MIGRATION_PASSWORD=... \
  REDIS_HOST=redis-host REDIS_PASSWORD=... \
  JWT_SECRET=... java -jar target/library-api-0.0.1-SNAPSHOT.jar
```

The title search needs the `pg_trgm` extension, which the migrations create; the owner of the tables needs the right to create objects in the `public` schema for that (the owner of the database has it).

The API listens on `http://localhost:8080/api/v1`. Try it as the administrator from `.env`:

```bash
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email": "admin@example.com", "password": "<ADMIN_PASSWORD from .env>"}' | jq -r .accessToken)
curl -i -X POST http://localhost:8080/api/v1/categories \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"name": "Science", "description": "Popular science books"}'
curl http://localhost:8080/api/v1/categories
```

To look at the database: `docker compose exec postgres psql -U library library` (the superuser from `.env`). To look at the cache: `docker compose exec redis sh -c 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli --scan'` lists the keys (`library-api:books::7`, `library-api:categories::all`, and the login counters `library-api:rate-limit:login:<address>`); `GET library-api:books::7` shows an entry as JSON.

A Postman collection and a local environment are in [postman/](postman/). Import both, select the "library-api local" environment and set `adminEmail` and `adminPassword` there to the values in `.env`. Run the "Auth" folder first: it logs in as the administrator, registers a new member, logs in as that member and saves both tokens in the environment, which the other folders send. The "Loans" folder borrows as that member; the "Admin jobs" folder starts the overdue check. Two requests in the "Books" folder call Open Library and need internet access. Watch the application log for the emails, SMS and audit lines; the "Categories" folder also sends its own request id.

### Run in Docker

The application can run as a container next to PostgreSQL and Redis. The image is built from the `Dockerfile` (the tests are not run in the build), and the container uses the `prod` profile:

```bash
docker compose --profile app up -d --build --wait
```

It listens on `http://localhost:8080` (`APP_PORT` in `.env`); stop `./mvnw spring-boot:run` first or choose another port. It keeps its tables in a schema of its own (`app`) and uses Redis database 1, so it does not see the data of the `dev` profile. `ADMIN_EMAIL` and `ADMIN_PASSWORD` create the administrator on the first start; `API_DOCS_ENABLED=true` turns Swagger UI on. `docker compose --profile app down` stops everything.

The containers run without root and without Linux capabilities, on a read-only file system; only the database volume and small temporary directories are writable.

To build only the image: `docker build -t library-api .`

## Test

```bash
./mvnw verify            # compile, run the tests and check code format
./mvnw spotless:apply    # fix code format
```

The tests run against real PostgreSQL and Redis in Docker containers, started and removed by the test run ([Testcontainers](https://testcontainers.com)); the first run downloads the images. `./mvnw verify` also writes a test coverage report to `target/site/jacoco/index.html`.

GitHub Actions runs `./mvnw verify` on every push and pull request, then builds the image, starts it with PostgreSQL and Redis, and scans it with [Trivy](https://trivy.dev): the run fails on a high or critical vulnerability that has a fix. The same checks run every Monday, so a vulnerability found after the last push shows up too. To scan a local image the same way:

```bash
docker save library-api:local --output image.tar
docker run --rm -v "$PWD/image.tar:/image.tar:ro" aquasec/trivy:0.76.2 \
  image --input /image.tar --scanners vuln --severity HIGH,CRITICAL --ignore-unfixed
```

## License

MIT. See [LICENSE](LICENSE).
