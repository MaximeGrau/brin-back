# Brin — backend (contexte pour Claude Code)

Réponds en **français**. Le développeur (Maxime) est dev backend Java expérimenté : pas besoin d'expliquer Spring, va droit au but.

## Le produit

**Brin** (nom provisoire) : app de shorts au format "scroll vertical" où une IA ne garde que les vidéos qui permettent de se cultiver, notées et classées. Ce dépôt importe les vidéos, les fait classer par l'IA et sert le feed à l'app mobile (`brin-mobile`, Expo/React Native).

## Stack

Java 25 · Spring Boot 4.1 (Spring Framework 7, Jackson 3, Hibernate 7) · Spring AI 2.0 (starter Google GenAI / Gemini) · Spring Data JPA · PostgreSQL 17 · Flyway · Maven.

## Commandes

```bash
mvn -N wrapper:wrapper        # une seule fois si mvnw est absent, puis committer le wrapper
./mvnw verify                 # build + génération OpenAPI + tests
./mvnw spring-boot:run        # nécessite Docker (compose.yaml) et GEMINI_API_KEY
```

Lance `./mvnw verify` après chaque modification.

## Règles du projet

- **Contract-first** : `src/main/resources/openapi/openapi.yaml` fait foi. Ne jamais écrire à la main les DTO ni les routes : modifier le contrat, regénérer (`./mvnw generate-sources`), puis implémenter l'interface générée (`com.maximegrau.brin.api.*Api`). Après un changement de contrat, rappeler de recopier le fichier dans `brin-mobile/api/openapi.yaml`.
- **Domaine séparé des DTO** : les entités et enums du domaine ne dépendent pas du code généré ; la conversion se fait dans des mappers dédiés.
- **Schéma géré par Flyway** (`db/migration`, `ddl-auto: validate`) : toute modification d'entité passe par une nouvelle migration `V{n}__description.sql`.
- **L'IA n'est jamais appelée pendant une requête du feed** : classification en tâche de fond, résultat stocké et mis en cache par vidéo (clé unique `source + external_id`).
- **Appels IA hors transaction** : seule l'écriture du résultat est transactionnelle (`TransactionTemplate`).
- **Règles YouTube** : pas de téléchargement/réhébergement de vidéos, pas de scraping d'Instagram/TikTok, respect des quotas de la YouTube Data API (privilégier `playlistItems.list` + `videos.list`, 1 unité / 50 vidéos ; éviter `search.list`, 100 unités). Les données YouTube stockées doivent être rafraîchies régulièrement (règles de l'API).
- Messages de commit en français.

## État actuel : squelette uniquement

Aucun code métier pour l'instant : `BrinApplication`, configuration, `compose.yaml`, contrat OpenAPI et génération des interfaces. Les opérations du contrat répondent `501 Not Implemented` (implémentations par défaut des interfaces générées) tant qu'aucun contrôleur ne les implémente.

Le squelette a été créé sans pouvoir compiler (Maven Central indisponible dans l'environnement de création). Au premier build, vérifier :
- les options du générateur OpenAPI pour Spring Boot 4 (`useSpringBoot4`, `useJackson3`) et le chemin de base `/api/v1` sur les interfaces générées ;
- le démarrage : `GEMINI_API_KEY` est obligatoire, Docker doit tourner (compose.yaml).

## Prochaines étapes

- [ ] Premier build vert (`./mvnw verify`) et démarrage local.
- [ ] Entité `Video` + première migration Flyway, puis `GET /feed` et `GET /categories`.
- [ ] Import YouTube : client YouTube Data API (clé `YOUTUBE_API_KEY`), liste de chaînes éducatives, filtre shorts (durée ≤ 3 min), job Spring Batch / `@Scheduled` quotidien.
- [ ] Classification IA sur métadonnées (Spring AI, sortie structurée : éducatif ?, catégorie, score 0–10, fiabilité, "À retenir"), en tâche de fond.
- [ ] Jeu de référence de ~200 vidéos notées à la main pour calibrer le prompt (viser 85–90 % d'accord).
- [ ] Étape 2 de l'entonnoir : analyse vidéo (Gemini à partir du lien YouTube) sur les vidéos retenues.
- [ ] Authentification (JWT) puis interactions utilisateur : J'ai appris / Enregistrer, bibliothèque, partages.
- [ ] Endpoint Approfondir (`GET /videos/{id}/deep-dive`).
- [ ] Tests d'intégration avec Testcontainers (PostgreSQL).

## Références

- Page projet Notion (concept, coûts IA, stock initial, concurrence, monétisation) : https://app.notion.com/p/3fef0cdd0e02813d96b9fe99afd3cc8d
- App mobile : https://github.com/MaximeGrau/brin-mobile
