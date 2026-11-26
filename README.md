# library-api

A backend for running a small library: the book catalog, its members and their loans. I built it step by step to learn Spring Boot, from a first endpoint to a containerized application with tests, monitoring and security checks.

## What it does

Catalog

- Librarians (administrators) add, change and remove books, authors and categories.
- Anyone can browse and search the catalog by title, author, category, year and whether a copy is free, page by page and in the order they choose.
- Adding a book with only its ISBN is enough: the title, description, year and page count are looked up in [Open Library](https://openlibrary.org).

Loans

- Members sign up, sign in and borrow books for 14 days, then return them.
- A book with no free copy cannot be borrowed, and when two members ask for the last copy at the same moment, only one gets it.
- A member can hold at most 3 books at once and cannot borrow while a book is overdue.
- Every night, loans past their due date are marked as overdue and the member gets a reminder by SMS. Members also get an email when they borrow or return a book. Messages are written to the log instead of being sent.

Accounts and safety

- Members see only their own loans; only librarians change the catalog or see everybody's loans.
- Passwords are stored only in a form that cannot be turned back into the password.
- Too many sign-in or sign-up attempts from one client are refused for a while.
- Every change is recorded: who did what, and whether it worked.

Running it

- Interactive API documentation where every request can be tried in the browser.
- Health and usage information for monitoring.
- Errors come back in one consistent format with a short code that says what went wrong.
- Popular pages are kept in a cache, so they load about twice as fast.

## Built with

Application

- Java 21 and Spring Boot 4.2 (Spring Framework 7), built with Maven through the Maven Wrapper
- Spring Web MVC for the REST API, with Bean Validation for the input checks
- Spring Security with JWT sign-in (JJWT) and BCrypt password hashing
- Spring Data JPA and Hibernate 7 on PostgreSQL 18, with Flyway for the database schema
- Spring Cache on Redis 8, and Redis again for the sign-in attempt limit
- Spring RestClient for the Open Library API, with Spring's retry and concurrency limit
- Spring scheduling, async tasks and application events for the nightly job and the emails
- Spring AOP for the audit log and the timing of slow calls
- Spring Boot Actuator and Micrometer for health checks and metrics
- springdoc-openapi for the Swagger UI documentation
- MapStruct and Lombok to keep the code short

Testing and quality

- JUnit, Mockito, AssertJ, Awaitility and Spring's MockMvc
- Testcontainers, so the tests use real PostgreSQL and Redis
- JaCoCo for test coverage
- Error Prone and Spotless (google-java-format) for code checks and format
- Postman collection for trying the API by hand

Running and delivery

- Docker and Docker Compose, with a hardened image that runs without root
- GitHub Actions for the build, the tests and the image, and Trivy to scan the image for known security issues

## Run

Needs JDK 21 or newer and Docker. Copy `.env.example` to `.env` and fill it in, then:

```bash
docker compose up -d --wait      # start PostgreSQL and Redis
./mvnw spring-boot:run           # start the application on http://localhost:8080
./mvnw verify                    # run the checks and the tests
```

The API documentation is at `http://localhost:8080/swagger-ui.html`.

## License

MIT. See [LICENSE](LICENSE).
