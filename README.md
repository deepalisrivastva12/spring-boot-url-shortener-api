# URL Shortener API

A production-style RESTful URL shortening service built with **Java 21 + Spring Boot 3**.
Implements the full CRUD lifecycle for short URLs plus access-count statistics,
a public redirect endpoint, layered architecture, validation, centralized
error handling, unit + integration tests, Swagger docs, and Docker support.

---

## Table of Contents
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [API Reference](#api-reference)
- [Design Decisions & Trade-offs](#design-decisions--trade-offs)
- [Testing](#testing)
- [Running with MySQL / Docker](#running-with-mysql--docker)
- [Possible Next Steps](#possible-next-steps)

---

## Architecture

```
Client                Frontend (static demo)              Backend (Spring Boot)         Database
──────                ───────────────────────             ─────────────────────         ────────
short.com/abc123 ──►  RedirectController (catch-all /*) ─► UrlShortenerService  ──►  UrlMappingRepository ──► H2 / MySQL
                       301 redirect to original URL          (business logic,             (Spring Data JPA)
                                                              validation, retry logic)

REST client      ──►  UrlController (/shorten/**)       ──► UrlShortenerService  ──► UrlMappingRepository ──► H2 / MySQL
(Postman, curl,        create / retrieve / update /
 the demo UI)          delete / stats
```

Layers, top to bottom:

- **Controller layer** – `UrlController` (the JSON REST API under `/shorten/**`)
  and `RedirectController` (the public `/{shortCode}` catch-all redirect,
  matching the "has a catch all route" behavior in the reference diagram).
  Controllers only translate HTTP ⇄ DTOs; no business logic lives here.
- **Service layer** – `UrlShortenerService` / `UrlShortenerServiceImpl` owns
  all business rules: short code generation + collision handling, access
  counting, and orchestrating repository calls inside transactions.
- **Repository layer** – `UrlMappingRepository` (Spring Data JPA) for
  persistence, including an atomic bulk-update query for the access counter.
- **Entity / DTO separation** – the JPA entity (`UrlMapping`) never leaves
  the service layer; controllers only ever see `UrlRequest` / `UrlResponse`
  DTOs. This keeps persistence concerns decoupled from the public API shape.
- **Cross-cutting concerns** – `GlobalExceptionHandler` (`@RestControllerAdvice`)
  converts exceptions into consistent JSON error bodies with correct status
  codes, in one place, instead of scattering try/catch through controllers.

## Tech Stack

| Concern              | Choice                                   |
|-----------------------|-------------------------------------------|
| Language / Runtime    | Java 21                                   |
| Framework             | Spring Boot 3.3 (Web, Data JPA, Validation, Actuator) |
| Database (default)    | H2 in-memory (zero setup, resets per run) |
| Database (optional)   | MySQL 8 (via `mysql` Spring profile)      |
| Build tool            | Maven                                     |
| Docs                  | springdoc-openapi (Swagger UI)            |
| Testing               | JUnit 5, Mockito, MockMvc, AssertJ        |
| Boilerplate reduction | Lombok                                    |
| Containerization      | Docker + docker-compose (app + MySQL)     |

## Project Structure

```
src/main/java/com/example/urlshortener/
├── UrlShortenerApplication.java
├── controller/
│   ├── UrlController.java        # POST/GET/PUT/DELETE /shorten/**, GET /shorten/{code}/stats
│   └── RedirectController.java   # GET /{code} -> 301 redirect (public short link)
├── service/
│   ├── UrlShortenerService.java
│   └── impl/UrlShortenerServiceImpl.java
├── repository/
│   └── UrlMappingRepository.java
├── entity/
│   └── UrlMapping.java
├── dto/
│   ├── UrlRequest.java            # validated inbound payload
│   ├── UrlResponse.java           # outbound payload (accessCount omitted unless set)
│   └── ErrorResponse.java
├── exception/
│   ├── UrlNotFoundException.java
│   ├── ShortCodeGenerationException.java
│   └── GlobalExceptionHandler.java
└── util/
    └── ShortCodeGenerator.java     # Base62 random code generator

src/main/resources/
├── application.yml                # default profile: H2
├── application-mysql.yml          # mysql profile
└── static/                        # tiny demo frontend (index.html/app.js/style.css)

src/test/java/.../
├── service/UrlShortenerServiceImplTest.java       # unit tests (Mockito)
└── controller/UrlControllerIntegrationTest.java   # full-stack MockMvc tests
```

## Getting Started

**Prerequisites:** Java 21+, Maven 3.9+ (or use the included `mvnw` if you add
the wrapper — plain `mvn` works fine too).

```bash
# Run tests
mvn test

# Run the app (default profile = H2, no external DB needed)
mvn spring-boot:run
```

The app starts on **http://localhost:8080**:
- Demo frontend: `http://localhost:8080/`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- H2 console: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:urlshortener`, user `sa`, empty password)
- Health check: `http://localhost:8080/actuator/health`

### Quick manual test with curl

```bash
# Create
curl -i -X POST http://localhost:8080/shorten \
  -H "Content-Type: application/json" \
  -d '{"url": "https://www.example.com/some/long/url"}'

# Retrieve (replace abc123 with the shortCode you got back)
curl -i http://localhost:8080/shorten/abc123

# Update
curl -i -X PUT http://localhost:8080/shorten/abc123 \
  -H "Content-Type: application/json" \
  -d '{"url": "https://www.example.com/some/updated/url"}'

# Stats
curl -i http://localhost:8080/shorten/abc123/stats

# Delete
curl -i -X DELETE http://localhost:8080/shorten/abc123

# Actual browser-style redirect (301 -> Location header)
curl -i http://localhost:8080/abc123
```

## API Reference

All endpoints match the spec exactly.

| Method | Path                        | Description                       | Success | Errors |
|--------|-----------------------------|------------------------------------|---------|--------|
| POST   | `/shorten`                  | Create a short URL                 | 201     | 400    |
| GET    | `/shorten/{shortCode}`      | Retrieve the original URL          | 200     | 404    |
| PUT    | `/shorten/{shortCode}`      | Update the destination URL         | 200     | 400, 404 |
| DELETE | `/shorten/{shortCode}`      | Delete a short URL                 | 204     | 404    |
| GET    | `/shorten/{shortCode}/stats`| Access statistics (`accessCount`)  | 200     | 404    |
| GET    | `/{shortCode}`              | Public redirect (bonus, not in spec)| 301    | 404    |

Example success body (`POST /shorten`):
```json
{
  "id": "1",
  "url": "https://www.example.com/some/long/url",
  "shortCode": "aZ3xQ9k",
  "createdAt": "2026-07-19T12:00:00Z",
  "updatedAt": "2026-07-19T12:00:00Z"
}
```

Example error body (any 4xx):
```json
{
  "timestamp": "2026-07-19T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "messages": ["url: url must be a valid http/https URL"],
  "path": "/shorten"
}
```

## Design Decisions & Trade-offs

A few choices worth being able to speak to in an interview:

- **Short code generation: random Base62, generated up front, with a
  collision retry loop** — rather than, say, encoding the auto-increment
  DB id. Random codes don't leak the total count of URLs created (an id-based
  scheme reveals business volume) and don't require a two-phase
  insert-then-encode-then-update dance. 7 characters of Base62 gives ~3.5
  trillion combinations, so collisions are rare, but the code still checks
  `existsByShortCode` and retries (bounded, to fail loudly instead of looping
  forever) rather than assuming they can't happen.

- **Access counting via an atomic bulk `UPDATE ... SET accessCount = accessCount + 1`**
  rather than read-modify-write in Java. Under concurrent redirects for a
  popular link, a naive "load entity, increment field, save" pattern loses
  updates (two threads can read the same starting value). The bulk update
  is a single SQL statement, so it doesn't have that race.

- **No caching layer on the redirect/read path.** An earlier draft cached
  `getOriginalUrl()` with Spring's `@Cacheable`, but that's a real bug
  waiting to happen: since access-count tracking is a side effect of that
  same method, a cache hit would skip the increment and silently
  under-report stats — one of the two things this service is explicitly
  supposed to get right. Correctness of stats was prioritized over shaving
  a single indexed lookup off the hot path. (See "Next Steps" for how you'd
  actually scale this correctly.)

- **DTOs are separate from the JPA entity.** `UrlMapping` (entity) never
  crosses the controller boundary. This means the persistence model can
  evolve (new columns, different indexing) without breaking the public API
  contract, and it stops Jackson from accidentally serializing JPA
  internals (lazy proxies, etc.).

- **Validation is declarative** (`@NotBlank`, `@Pattern` on `UrlRequest`)
  and centrally translated into `400`s by `GlobalExceptionHandler`, instead
  of manual `if` checks scattered through the controller.

- **The stats endpoint doesn't mutate state.** `getStats()` deliberately
  does *not* increment `accessCount` — checking the stats for a link isn't
  the same as a visitor using it. Only `getOriginalUrl()` (called by both
  the JSON retrieve endpoint and the redirect endpoint) counts as an access.

- **H2 by default, MySQL via a Spring profile.** The project runs with zero
  setup (`mvn spring-boot:run`) for quick evaluation, while `application-mysql.yml`
  and `docker-compose.yml` show it's equally ready for a real relational
  database — same JPA code, different `spring.datasource.*` config.

## Testing

```bash
mvn test
```

- **`UrlShortenerServiceImplTest`** — unit tests for the service layer with
  Mockito-mocked repository/generator: happy paths, the collision-retry
  loop, the "give up after N attempts" failure mode, and every
  not-found (404) branch.
- **`UrlControllerIntegrationTest`** — full Spring context + MockMvc tests
  hitting real HTTP verbs against a real (in-memory H2) database: the full
  create → retrieve → update → stats → delete lifecycle in one test, plus
  validation failures (400) and not-found cases (404) for every endpoint,
  plus the 301 redirect behavior.

## Running with MySQL / Docker

```bash
# Everything (app + MySQL) via Docker Compose
docker-compose up --build
```

Or run MySQL yourself and point the app at it:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=mysql \
  -Dspring-boot.run.arguments="--DB_HOST=localhost --DB_PASSWORD=yourpassword"
```

## Possible Next Steps

Things intentionally left out of scope for this project, but worth
mentioning if asked "how would you extend this":

- **Async/batched access-count writes** (e.g. increment in-memory /
  in Redis, flush to the DB periodically) to remove the write-per-redirect
  bottleneck at very high traffic, without the caching correctness issue
  described above.
- **Rate limiting** on `POST /shorten` to prevent abuse.
- **Auth** (API keys or user accounts) so short URLs can be scoped per user
  — explicitly out of scope per the requirements.
- **Custom short codes** (user-chosen alias instead of random).
- **Expiring links** (`expiresAt` column + scheduled cleanup job).
- **Pagination** on a "list my URLs" endpoint if that were added.
