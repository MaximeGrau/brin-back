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
- **Règles YouTube** : pas de téléchargement/réhébergement de vidéos YouTube/Instagram/TikTok (les vidéos envoyées par les utilisateurs eux-mêmes sur Brin, elles, sont stockées par Brin), pas de scraping d'Instagram/TikTok, respect des quotas de la YouTube Data API (privilégier `playlistItems.list` + `videos.list`, 1 unité / 50 vidéos ; éviter `search.list`, 100 unités). Les données YouTube stockées doivent être rafraîchies régulièrement (règles de l'API).
- Messages de commit en français.

## État actuel : squelette uniquement

Aucun code métier pour l'instant : `BrinApplication`, configuration, `compose.yaml`, contrat OpenAPI et génération des interfaces. Les opérations du contrat répondent `501 Not Implemented` (implémentations par défaut des interfaces générées) tant qu'aucun contrôleur ne les implémente.

Le squelette a été créé sans pouvoir compiler (Maven Central indisponible dans l'environnement de création). Au premier build, vérifier :
- les options du générateur OpenAPI pour Spring Boot 4 (`useSpringBoot4`, `useJackson3`) et le chemin de base `/api/v1` sur les interfaces générées ;
- le démarrage : `GEMINI_API_KEY` est obligatoire, Docker doit tourner (compose.yaml).

## Authentification

- JWT HS256 émis par le backend (`JwtTokens`, 15 min) + jeton de rafraîchissement opaque à usage unique (60 j, hash en base). `CurrentUser` lit le sujet du JWT.
- OAuth Google / TikTok / Facebook (Meta) entièrement côté backend : `/auth/oauth/{provider}/authorize` → fournisseur → `/callback` → redirection vers le lien profond de l'app avec un ticket (`POST /auth/oauth/exchange`). Fournisseur sans identifiants = désactivé (`error=unavailable`).
- URL de retour à déclarer chez chaque fournisseur : `{PUBLIC_BASE_URL}/api/v1/auth/oauth/{google|tiktok|facebook}/callback` (HTTPS : en local, tunnel type ngrok).
- Facebook plutôt qu'Instagram : la connexion Instagram grand public n'existe plus (API Basic Display fermée en décembre 2024).
- E-mails en dev : Mailpit (compose.yaml), http://localhost:8025. Si Postgres tournait déjà, Spring Boot ne relance pas `docker compose up` : `docker compose up -d mailpit`.

## Feed "Pour toi"

- Part de chaque catégorie (`CategoryShares`) : poids = base (3 si choisie, 1 sinon) + affinité apprise ; part = 85 % au prorata des poids + 15 % à parts égales (aucune catégorie sous ~1,9 %). Réglages : `brin.feed.*`.
- Affinité (`user_category_affinity`) : +0,6 par "J'ai appris" (retiré si décoché), +0,1 par boucle dans la limite de 3 par vidéo (`video_interaction.loop_count`), −0,2 par vidéo swipée en moins d'une demi-seconde (une fois par vidéo, `skip_count`), plancher −2 ; le poids ne descend pas sous 0.
- Ordre aléatoire par session : tri SQL sur `md5(graine || id)` (`ShuffleOrder`, même calcul en Java pour la position) ; nouvelle graine à chaque première page, gardée dans le curseur (`MixedFeedCursor`) pour paginer sans doublon.
- Mélange (`FeedMixer`) : catégorie de chaque emplacement tirée au hasard selon les parts ; jamais deux vidéos du même créateur à la suite tant qu'une autre possibilité existe (vidéos mises de côté, 5 au plus par catégorie, servies ensuite en priorité, transmises dans le curseur ; dernier créateur aussi, pour la page suivante). Une catégorie vide est retirée et sa part redistribuée.
- `GET /feed?category=X` : même mécanique, limitée à cette catégorie. Le score IA ne joue plus sur l'ordre (seulement sur l'entrée dans le feed, `min-score`).

## Vidéos envoyées sur Brin (source BRIN)

Objectif : un catalogue propre, lu dans notre lecteur plein écran, pour se passer peu à peu des Shorts YouTube.
- `POST /uploads` (multipart, ouvert à tout utilisateur à l'adresse vérifiée) : `UploadService` vérifie le fichier lui-même (`Mp4Inspector` lit durée et dimensions dans les boîtes MP4/MOV, rotation comprise) selon `brin.upload.*` (vertical ≥ 1,25, 3 s à 3 min, 100 Mo, 5 en analyse max), stocke via `VideoStorage` (`LocalVideoStorage` dans `./data/uploads` ; à remplacer par S3/R2 en prod), crée la vidéo PENDING sous un créateur `Creator.forUser`.
- Classification prioritaire : `UploadedVideoClassifier` envoie le fichier à l'API Files de Gemini, attend `ACTIVE`, fait regarder la vidéo (modération comprise, prompt `classification-upload-v1`), supprime le fichier chez Google. Après `max-attempts` échecs, la vidéo est refusée (pas d'analyse infinie sur le tableau de bord).
- Lecture : liens signés HMAC à durée limitée (`MediaUrls`, `GET /media/{video|thumbnail}/{id}?exp&sig`, Range géré par Spring), dans `FeedItem.playbackUrl` et `Upload.playbackUrl`. Liens construits sur l'hôte de la requête (en prod derrière un proxy : `server.forward-headers-strategy`).
- Tableau de bord : `GET /me/uploads` (statut ANALYSING/PUBLISHED/REJECTED, raison du refus, "J'ai appris" reçus), `DELETE /me/uploads/{id}`.

## Prochaines étapes

- [ ] Premier build vert (`./mvnw verify`) et démarrage local.
- [ ] Entité `Video` + première migration Flyway, puis `GET /feed` et `GET /categories`.
- [ ] Import YouTube : client YouTube Data API (clé `YOUTUBE_API_KEY`), liste de chaînes éducatives, filtre shorts (durée ≤ 3 min), job Spring Batch / `@Scheduled` quotidien.
- [ ] Classification IA sur métadonnées (Spring AI, sortie structurée : éducatif ?, catégorie, score 0–10, fiabilité, "À retenir"), en tâche de fond.
- [ ] Jeu de référence de ~200 vidéos notées à la main pour calibrer le prompt (viser 85–90 % d'accord).
- [ ] Étape 2 de l'entonnoir : analyse vidéo (Gemini à partir du lien YouTube) sur les vidéos retenues.
- [x] Authentification : e-mail + mot de passe, Google / TikTok / Facebook (OAuth côté backend, `service/auth`), code de validation par e-mail, catégories préférées (`PUT /me/categories`).
- [x] Feed personnalisé (catégories choisies, "J'ai appris", boucles) et interactions J'ai appris / Enregistrer.
- [ ] Affinité qui s'estompe avec le temps (les goûts changent) ; ne plus resservir les vidéos déjà vues d'une session à l'autre.
- [ ] Élargir la liste de créateurs (3 aujourd'hui : les répétitions de créateur deviennent inévitables en fin de feed).
- [x] Bibliothèque : shorts enregistrés (`GET /library`) et compteurs du profil (`GET /me/stats`).
- [ ] Partages depuis Instagram / TikTok vers la bibliothèque.
- [ ] Vidéos envoyées : stockage objet + CDN en prod, transcodage (HLS, plusieurs qualités) si les fichiers bruts pèsent trop, signalement par les utilisateurs et CGU (hébergeur de contenus : obligations du DSA), éventuellement un coup de pouce aux vidéos BRIN dans le feed.
- [ ] Endpoint Approfondir (`GET /videos/{id}/deep-dive`).
- [ ] Tests d'intégration avec Testcontainers (PostgreSQL).

## Références

- Page projet Notion (concept, coûts IA, stock initial, concurrence, monétisation) : https://app.notion.com/p/3fef0cdd0e02813d96b9fe99afd3cc8d
- App mobile : https://github.com/MaximeGrau/brin-mobile
