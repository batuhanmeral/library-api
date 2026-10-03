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

Work in progress. Available now: create, list, read, update and delete book categories under `/api/v1/categories`. Category names are unique, up to 100 characters; descriptions are up to 500 characters. Data is kept in an in-memory database and is lost when the application stops.

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
| `VALIDATION_FAILED` | 400 | A field is missing or invalid; `errors` lists each field |
| `MALFORMED_REQUEST` | 400 | The body is not valid JSON or a path value has the wrong type |
| `RESOURCE_NOT_FOUND` | 404 | No resource with that id, or no such path |
| `METHOD_NOT_ALLOWED` | 405 | The path does not support the HTTP method |
| `DUPLICATE_RESOURCE` | 409 | The name is already taken |
| `DATA_CONFLICT` | 409 | The change conflicts with existing data |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | The body is not `application/json` |
| `REQUEST_REJECTED` | 4xx | Any other client error |
| `INTERNAL_ERROR` | 500 | Unexpected server error |

## Requirements

- JDK 21 or newer. Maven is not needed; the Maven Wrapper (`./mvnw`) downloads it.

## Run

```bash
./mvnw spring-boot:run
```

The API listens on `http://localhost:8080/api/v1`. Try it:

```bash
curl -i -X POST http://localhost:8080/api/v1/categories \
  -H 'Content-Type: application/json' \
  -d '{"name": "Science", "description": "Popular science books"}'
curl http://localhost:8080/api/v1/categories
```

The H2 database console is at `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:library`, user `sa`, empty password).

A Postman collection and a local environment are in [postman/](postman/). Import both and select the "library-api local" environment.

## Test

```bash
./mvnw verify            # compile, run the tests and check code format
./mvnw spotless:apply    # fix code format
```

## License

MIT. See [LICENSE](LICENSE).
