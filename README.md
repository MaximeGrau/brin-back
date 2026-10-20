# brin-back

Backend de **Brin** (nom provisoire) : import des shorts, classification par IA
et API du feed consommée par l'app mobile [`brin-mobile`](https://github.com/MaximeGrau/brin-mobile).

**Java 25 · Spring Boot 4.1 · Spring AI 2.0 (Gemini) · Spring Data JPA · PostgreSQL · Flyway · Maven**

## Démarrer

Prérequis : JDK 25, Maven 3.9+, Docker (pour PostgreSQL), une clé API Gemini
(gratuite sur [Google AI Studio](https://aistudio.google.com/apikey)).

```bash
# Une seule fois : ajouter le wrapper Maven au projet (puis le committer)
mvn -N wrapper:wrapper

export GEMINI_API_KEY=ta-cle
./mvnw spring-boot:run
```

PostgreSQL démarre tout seul depuis `compose.yaml`.

```bash
curl http://localhost:8080/actuator/health
```

## Contract-first

`src/main/resources/openapi/openapi.yaml` **fait foi**. À chaque build, le plugin
`openapi-generator-maven-plugin` génère dans `target/generated-sources/openapi` :

- les interfaces Spring (`com.maximegrau.brin.api.FeedApi`, `InteractionsApi`, `VideosApi`, `LibraryApi`) ;
- les DTO (`com.maximegrau.brin.api.model.*`).

Les contrôleurs implémenteront ces interfaces (ex. `FeedController implements FeedApi`).

Après une modification du contrat, recopier le fichier dans `brin-mobile/api/openapi.yaml`
puis lancer `npm run api:generate` côté mobile.

## Structure

```
pom.xml                                   Dépendances + génération OpenAPI
compose.yaml                              PostgreSQL de développement
src/main/java/com/maximegrau/brin/        Code (BrinApplication)
src/main/resources/application.yaml       Configuration
src/main/resources/openapi/openapi.yaml   Contrat d'API (source de vérité)
src/main/resources/db/migration/          Migrations Flyway (V1__..., V2__...)
```
