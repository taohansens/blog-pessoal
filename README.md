# Blog Pessoal API

API REST reativa (Spring WebFlux) para gerenciamento de posts e mídias de blog, com autenticação GitHub OAuth2, emissão de JWT apenas para administradores e persistência no CouchDB.

## Tecnologias principais
- Java 21
- Spring Boot (WebFlux, Security, Validation)
- CouchDB
- Cloudinary (mídias)
- Maven
- Docker / Docker Compose

## Pré-requisitos
- Java 21+
- Maven 3.9+ (ou usar `./mvnw`)
- CouchDB 3.x (ou subir via Docker Compose)
- Docker e Docker Compose (opcional)

## Configuração rápida
Variáveis de ambiente principais (veja também `application.yml`):
```
COUCHDB_URI=http://localhost:5986
COUCHDB_USER=admin
COUCHDB_PASSWORD=admin
COUCHDB_DB_NAME=blog
CORS_ALLOWED_ORIGINS=http://localhost:3000
CLOUDINARY_CLOUD_NAME=...
CLOUDINARY_API_KEY=...
CLOUDINARY_API_SECRET=...
APP_ADMIN_EMAIL=seu-email-admin@dominio.com
APP_JWT_SECRET=chave-secreta-256bits
APP_JWT_EXPIRATION=86400000
```

Porta padrão: `9899`.

## Executar local
```bash
./mvnw spring-boot:run
# ou
mvn spring-boot:run
```

## Executar com Docker Compose
```bash
docker-compose up -d --build
```
Use um `.env` com as variáveis acima se preferir.

## Autenticação e autorização
- Login via GitHub OAuth2.
- JWT emitido somente para usuários cujo e-mail/login coincide com `app.admin.email`.
- Filtro `AdminAuthorizationFilter` protege escritas em posts e todas as rotas de mídia.
- Endpoints públicos: leitura de posts (`/api/posts/**`), auth (`/api/auth/**`), OAuth2 login.

## Endpoints principais
- Públicos:
  - `GET /api/posts/all` — lista posts publicados (mais recentes).
  - `GET /api/posts/{slug}` — detalha post.
  - `GET /api/posts?page=&size=` — paginação de posts.
- Admin (JWT/OAuth2 admin):
  - `GET /api/admin/posts` (paginado), `GET /api/admin/posts/all`, `GET /api/admin/posts/{slug}`
  - `POST /api/admin/posts`, `PUT /api/admin/posts/{id}`, `DELETE /api/admin/posts/{id}`
  - Mídias: `POST /api/admin/media/upload`, `GET /api/admin/media`, `DELETE /api/admin/media/{publicId}`
- Auth helpers:
  - `GET /api/auth/me` — info do usuário autenticado (admin).
  - `GET /api/auth/token` — retorna token JWT pós OAuth2 (admin).

## Tratamento de erros
`GlobalExceptionHandler` padroniza respostas com timestamp, status, error e message.

## Estrutura (resumo)
```
src/main/java/br/com/taohansen/blog/
  BlogpessoaltaoApplication.java
  config/         # CORS, Cloudinary, Security, WebClient, favicon 204
  controllers/    # públicos, admin, auth, exceptions
  controllers/admin/
  dto/post/       # responses
  mappers/        # MapStruct DTO mapping
  models/         # domain/requests/views
  repository/     # CouchDbRepository (WebClient)
  security/       # JWT, OAuth2 success, admin auth
  services/       # Post, Slug, Cloudinary, mappers
```

## Build e testes
```bash
./mvnw clean package
./mvnw test
```

## Licença
MIT