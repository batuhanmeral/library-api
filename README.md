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

- Category names are unique, up to 100 characters; descriptions are up to 500 characters.
- A book has a title, an optional unique ISBN (10 or 13 digits), one author and any number of categories, sent as `authorId` and `categoryIds`. All copies of a new book are available; changing `totalCopies` changes `availableCopies` by the same amount.
- `PUT /books/{id}` replaces the whole book; `PATCH /books/{id}` changes only the fields that are sent. A field cannot be cleared with `PATCH` (a `null` value is treated as not sent). A book can have at most 20 categories.
- An author who has books, or a category that books use, cannot be deleted.
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
| `RESOURCE_IN_USE` | 409 | The author has books, or books use the category |
| `DATA_CONFLICT` | 409 | The change conflicts with existing data |
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

A Postman collection and a local environment are in [postman/](postman/). Import both and select the "library-api local" environment.

## Test

```bash
./mvnw verify            # compile, run the tests (in-memory H2) and check code format
./mvnw spotless:apply    # fix code format
```

## License

MIT. See [LICENSE](LICENSE).
