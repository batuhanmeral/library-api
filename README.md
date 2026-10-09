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
- Sign in with JWT; administrators and members have different permissions, and members see only their own loans.
- Frequently read data is cached in Redis.
- Interactive API documentation with Swagger UI.

## Status

Work in progress. Available now, under `/api/v1`:

| Resource | Endpoints |
|---|---|
| Categories | `GET /categories`, `GET /categories/{id}`, `POST /categories`, `PUT /categories/{id}`, `DELETE /categories/{id}` |
| Authors | `GET /authors`, `GET /authors/{id}`, `GET /authors/{id}/books`, `POST /authors`, `PUT /authors/{id}`, `DELETE /authors/{id}` |
| Books | `GET /books`, `GET /books/{id}`, `POST /books`, `PUT /books/{id}`, `PATCH /books/{id}`, `DELETE /books/{id}` |
| Loans | `POST /loans`, `POST /loans/{id}/return`, `GET /loans/{id}`, `GET /loans` |
| Jobs | `POST /admin/jobs/overdue-check` |

- Category names are unique, up to 100 characters; descriptions are up to 500 characters.
- A book has a title, an optional unique ISBN (10 or 13 digits), one author and any number of categories, sent as `authorId` and `categoryIds`. All copies of a new book are available; changing `totalCopies` changes `availableCopies` by the same amount.
- `POST /books` with an `isbn` fills in a missing title, description, publication year and page count from [Open Library](https://openlibrary.org); see below. The title may then be left out.
- `PUT /books/{id}` replaces the whole book; `PATCH /books/{id}` changes only the fields that are sent. A field cannot be cleared with `PATCH` (a `null` value is treated as not sent). A book can have at most 20 categories.
- An author who has books, a category that books use, or a book that has loans cannot be deleted.
- Every response has `createdAt` and `updatedAt` timestamps.

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
  -H 'Content-Type: application/json' \
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

Borrow a copy with `POST /loans` and `{"bookId": 1, "memberId": 1}`; the loan is due 14 days later. Return it with `POST /loans/{id}/return`. `GET /loans` is paged (newest first) and takes optional `status` (`ACTIVE`, `OVERDUE`, `RETURNED`) and `memberId` filters; it can be sorted by `id`, `loanDate`, `dueDate`, `returnDate` and `createdAt`.

- Only members can borrow; an administrator cannot.
- A book with no available copy cannot be borrowed. When several members borrow the same book at the same moment, the requests are retried as needed: each gets a copy while copies last, the others get `BOOK_NOT_AVAILABLE`.
- A member cannot borrow a second copy of a book they already have on loan.
- A member can have at most 3 open (active or overdue) loans.
- A member with a loan past its due date cannot borrow until it is returned.
- `totalCopies` of a book cannot be set below the number of copies on loan.

There are no user accounts yet: the member is sent in the request. With the `dev` profile, a demo member (`member@example.com`, phone `+1 555 555 0100`, a number reserved for fiction) is created at startup; on a new database its id is 1.

### Notifications and the nightly overdue check

- After a loan or a return is saved, the member gets a confirmation email.
- Every night at 02:00 (Europe/Istanbul), active loans past their due date are marked `OVERDUE` and each member concerned gets one SMS reminder, if they have a phone number. Running the check again changes nothing.
- `POST /admin/jobs/overdue-check` starts the same check at once and returns `202 Accepted` without waiting for it; while a run is going, another start gets `409 JOB_ALREADY_RUNNING`. Until sign-in exists, anyone can call it: do not expose the application publicly yet.

Emails and SMS are not really sent; they are written to the application log, with the address shortened to its first letter and domain (`m***@example.com`) and the phone number to its last two digits (`***00`).

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
```

They can be overridden like any Spring Boot property, for example with `LIBRARY_LOAN_MAX_ACTIVE_LOANS=5`. The application does not start if a value is missing or invalid (a limit below 1, an unknown time zone).

Data is stored in PostgreSQL.

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
| `MALFORMED_REQUEST` | 400 | The body is not valid JSON or a path value has the wrong type |
| `INVALID_PARAMETER` | 400 | A query parameter cannot be used: a sort field that is not allowed, or `yearFrom` after `yearTo` |
| `RESOURCE_NOT_FOUND` | 404 | No resource with that id (also an `authorId` or `categoryIds` entry in a book), or no such path |
| `METHOD_NOT_ALLOWED` | 405 | The path does not support the HTTP method |
| `DUPLICATE_RESOURCE` | 409 | The name or ISBN is already taken |
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
| `NOT_A_MEMBER` | 422 | The borrower is not a member (for example an administrator) |
| `ISBN_NOT_FOUND` | 422 | A new book has no title and Open Library does not know its ISBN |
| `EXTERNAL_SERVICE_UNAVAILABLE` | 503 | A new book has no title and Open Library could not be reached; send the title or try again later |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | The body is not `application/json` |
| `REQUEST_REJECTED` | 4xx | Any other client error |
| `INTERNAL_ERROR` | 500 | Unexpected server error |

## Requirements

- JDK 21 or newer. Maven is not needed; the Maven Wrapper (`./mvnw`) downloads it.
- Docker with Docker Compose, for the PostgreSQL database. The tests do not need it.

## Run

Copy the example settings, set a password in `.env`, start PostgreSQL and the application:

```bash
cp .env.example .env
docker compose up -d --wait
./mvnw spring-boot:run
```

`./mvnw spring-boot:run` uses the `dev` profile: it connects to the database from `docker-compose.yml` on `localhost:5434` (`POSTGRES_PORT` in `.env`; change it if the port is busy) and logs SQL statements. The schema is created and updated by Flyway at startup. `docker compose down` stops the database and keeps the data; `docker compose down -v` deletes it too.

To run the packaged application elsewhere, use the `prod` profile and pass the connection as environment variables:

```bash
./mvnw package
SPRING_PROFILES_ACTIVE=prod DB_URL=jdbc:postgresql://db-host:5432/library \
  DB_USERNAME=library DB_PASSWORD=... java -jar target/library-api-0.0.1-SNAPSHOT.jar
```

The API listens on `http://localhost:8080/api/v1`. Try it:

```bash
curl -i -X POST http://localhost:8080/api/v1/categories \
  -H 'Content-Type: application/json' \
  -d '{"name": "Science", "description": "Popular science books"}'
curl http://localhost:8080/api/v1/categories
```

To look at the database: `docker compose exec postgres psql -U library library`.

A Postman collection and a local environment are in [postman/](postman/). Import both and select the "library-api local" environment. The "Loans" folder borrows with the demo member (`memberId` in the environment); the "Admin jobs" folder starts the overdue check. Two requests in the "Books" folder call Open Library and need internet access. Watch the application log for the emails and SMS.

## Test

```bash
./mvnw verify            # compile, run the tests (in-memory H2) and check code format
./mvnw spotless:apply    # fix code format
```

## License

MIT. See [LICENSE](LICENSE).
