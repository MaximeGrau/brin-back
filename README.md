# brin-back

Backend of **Brin**, a mobile app for scrolling short videos in the TikTok / Instagram style, but showing
only educational content. This service imports shorts from a curated list of YouTube channels, classifies
them with an LLM, and serves the feed to the mobile app
[`brin-mobile`](https://github.com/MaximeGrau/brin-mobile).

**Java 25 · Spring Boot 4.1 · Spring AI 2.0 (Gemini) · Spring Data JPA · PostgreSQL · Flyway · Maven**

> Brin started as a hands-on way to learn Spring AI and to see how a Spring service behaves when an LLM is
> part of the request path. It is built with the help of coding agents (see [`CLAUDE.md`](CLAUDE.md)).

## Getting started

Requirements: JDK 25, Maven 3.9+, Docker (for PostgreSQL), and a Gemini API key
(free on [Google AI Studio](https://aistudio.google.com/apikey)).

```bash
export GEMINI_API_KEY=your-key
export YOUTUBE_API_KEY=your-key   # optional: without it, the YouTube import is skipped
mvn spring-boot:run
```

PostgreSQL starts automatically from `compose.yaml`.

```bash
curl http://localhost:8080/actuator/health
```

Build and run the tests:

```bash
mvn verify
```

## Contract-first API

`src/main/resources/openapi/openapi.yaml` is the source of truth. On every build,
`openapi-generator-maven-plugin` generates into `target/generated-sources/openapi`:

- the Spring interfaces (`com.maximegrau.brin.api.FeedApi`, `InteractionsApi`, `VideosApi`, `LibraryApi`);
- the DTOs (`com.maximegrau.brin.api.model.*`).

Controllers implement those interfaces (for example `FeedController implements FeedApi`).

After a contract change, copy the file to `brin-mobile/api/openapi.yaml` and run `npm run api:generate`
on the mobile side.

## Project layout

```
pom.xml                                   Dependencies + OpenAPI generation
compose.yaml                              Development PostgreSQL
src/main/java/com/maximegrau/brin/        Code (BrinApplication)
src/main/resources/application.yaml       Configuration
src/main/resources/openapi/openapi.yaml   API contract (source of truth)
src/main/resources/db/migration/          Flyway migrations (V1__..., V2__...)
```

## Configuration

Secrets are never committed: they are read from environment variables (`GEMINI_API_KEY`, `YOUTUBE_API_KEY`,
`JWT_SECRET`, and the optional OAuth client ids and secrets). In production, `JWT_SECRET` is mandatory
(32 random bytes, `openssl rand -base64 32`).
