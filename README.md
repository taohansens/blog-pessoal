# Blog Pessoal API

API REST reativa para gerenciamento de posts de blog, desenvolvida com Spring Boot WebFlux e integrada com CouchDB.

## 🚀 Tecnologias

- **Java 21**
- **Spring Boot 4.0.0**
- **Spring WebFlux**
- **Apache CouchDB**
- **Lombok**
- **Maven**
- **Docker**

## 📋 Pré-requisitos

- Java 21 ou superior
- Maven 3.6+
- CouchDB 3.x (ou usar Docker Compose)
- Docker e Docker Compose (opcional)

## 🛠️ Instalação e Execução

### Opção 1: Execução Local

1. **Clone o repositório**
```bash
git clone <url-do-repositorio>
cd blogpessoaltao
```

2. **Configure as variáveis de ambiente**
```bash
export COUCHDB_URI=http://localhost:5984
export COUCHDB_USER=admin
export COUCHDB_PASSWORD=admin
export COUCHDB_DB_NAME=blog
export CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:8080
```

3. **Execute a aplicação**
```bash
./mvnw spring-boot:run
```

Ou usando Maven instalado:
```bash
mvn spring-boot:run
```

### Opção 2: Docker Compose (Recomendado)

1. **Crie um arquivo `.env`** (opcional)
```bash
COUCHDB_URI=http://couchdb:5984
COUCHDB_USER=admin
COUCHDB_PASSWORD=admin
COUCHDB_DB_NAME=blog
CORS_ALLOWED_ORIGINS=http://localhost:3000,https://meusite.com
```

2. **Execute com Docker Compose**
```bash
docker-compose up -d --build
```

Para mais detalhes sobre Docker, consulte [DOCKER.md](./DOCKER.md).

## 📡 Endpoints da API

Base URL: `http://localhost:9899/api/posts`

### 1. Listar Todos os Posts

Retorna todos os posts ordenados por data (mais recentes primeiro).

**GET** `/api/posts/all`

**Resposta 200:**
```json
[
  {
    "id": "post-meu-primeiro-post",
    "title": "Meu Primeiro Post",
    "slug": "meu-primeiro-post",
    "date": "2024-01-15",
    "tags": ["java", "spring"],
    "summary": "Resumo do post",
    "content": "Conteúdo completo do post..."
  }
]
```

### 2. Buscar Post por Slug

Retorna um post específico pelo seu slug.

**GET** `/api/posts/{slug}`

**Parâmetros:**
- `slug` (path): Slug do post (ex: `meu-primeiro-post`)

**Resposta 200:**
```json
{
  "id": "post-meu-primeiro-post",
  "title": "Meu Primeiro Post",
  "slug": "meu-primeiro-post",
  "date": "2024-01-15",
  "tags": ["java", "spring"],
  "summary": "Resumo do post",
  "content": "Conteúdo completo do post..."
}
```

**Resposta 404:** Post não encontrado

### 3. Listar Posts Paginados

Retorna posts paginados com metadados (sem conteúdo completo).

**GET** `/api/posts?page={page}&size={size}`

**Parâmetros:**
- `page` (query, opcional): Número da página (padrão: 0)
- `size` (query, opcional): Tamanho da página (padrão: 10, máximo: 50)

**Exemplo:**
```
GET /api/posts?page=0&size=10
```

**Resposta 200:**
```json
{
  "posts": [
    {
      "id": "post-meu-primeiro-post",
      "title": "Meu Primeiro Post",
      "slug": "meu-primeiro-post",
      "date": "2024-01-15",
      "tags": ["java", "spring"],
      "summary": "Resumo do post"
    }
  ],
  "page": 0,
  "size": 10,
  "total": 25,
  "hasNext": true
}
```

## ⚙️ Configuração

### Variáveis de Ambiente

| Variável | Descrição | Padrão |
|----------|-----------|--------|
| `COUCHDB_URI` | URI do CouchDB | `http://localhost:5986` |
| `COUCHDB_USER` | Usuário do CouchDB | - |
| `COUCHDB_PASSWORD` | Senha do CouchDB | - |
| `COUCHDB_DB_NAME` | Nome do banco de dados | `blog` |
| `CORS_ALLOWED_ORIGINS` | Origens permitidas (separadas por vírgula) | `localhost` |

### application.yml

O arquivo `src/main/resources/application.yml` contém as configurações padrão:

```yaml
server:
  port: 9899

couchdb:
  uri: ${COUCHDB_URI:http://localhost:5986}
  username: ${COUCHDB_USER}
  password: ${COUCHDB_PASSWORD}
  database: ${COUCHDB_DB_NAME:blog}

cors:
  allowed-origins: ${CORS_ALLOWED_ORIGINS:localhost}
```

## 📁 Estrutura do Projeto

```
blogpessoaltao/
├── src/
│   ├── main/
│   │   ├── java/br/com/taohansen/blog/
│   │   │   ├── BlogpessoaltaoApplication.java
│   │   │   ├── config/
│   │   │   │   ├── CorsConfig.java          # Configuração CORS
│   │   │   │   └── CouchDbWebClientConfig.java  # Configuração WebClient
│   │   │   ├── controllers/
│   │   │   │   ├── PostsController.java      # Endpoints REST
│   │   │   │   └── GlobalExceptionHandler.java  # Tratamento de erros
│   │   │   ├── models/
│   │   │   │   ├── Post.java                # Modelo completo do post
│   │   │   │   ├── PostMetadata.java        # Metadados do post
│   │   │   │   ├── PagedPostsResponse.java  # Resposta paginada
│   │   │   │   └── PostsViewResponse.java   # Resposta do CouchDB
│   │   │   └── services/
│   │   │       └── CouchDbService.java      # Lógica de negócio
│   │   └── resources/
│   │       └── application.yml              # Configurações
│   └── test/                                # Testes
├── docker-compose.yml                       # Orquestração Docker
├── Dockerfile                              # Build da imagem
├── pom.xml                                 # Dependências Maven
└── README.md                               # Este arquivo
```

## 🔒 CORS

A API suporta configuração de CORS via variável de ambiente `CORS_ALLOWED_ORIGINS`. Você pode especificar múltiplas origens separadas por vírgula:

```bash
export CORS_ALLOWED_ORIGINS=http://localhost:3000,https://meusite.com,https://www.meusite.com
```

## 🐳 Docker

Para executar com Docker, consulte o arquivo [DOCKER.md](./DOCKER.md) para instruções detalhadas.

**Comando rápido:**
```bash
docker-compose up -d --build
```

## 🧪 Testes

Execute os testes com:
```bash
./mvnw test
```

## 📝 Modelos de Dados

### Post
```json
{
  "id": "string",
  "_rev": "string",
  "type": "blog_post",
  "title": "string",
  "slug": "string",
  "date": "YYYY-MM-DD",
  "tags": ["string"],
  "summary": "string",
  "content": "string"
}
```

### PostMetadata
```json
{
  "id": "string",
  "slug": "string",
  "title": "string",
  "date": "YYYY-MM-DD",
  "tags": ["string"],
  "summary": "string"
}
```

## 🚨 Tratamento de Erros

A API retorna códigos HTTP apropriados:

- **200 OK**: Requisição bem-sucedida
- **400 Bad Request**: Parâmetros inválidos
- **404 Not Found**: Recurso não encontrado
- **500 Internal Server Error**: Erro interno do servidor

Exemplo de resposta de erro:
```json
{
  "timestamp": "2024-01-15T10:30:00",
  "status": 404,
  "error": "Not Found",
  "message": "Post não encontrado"
}
```

## 🔧 Desenvolvimento

### Build do Projeto
```bash
./mvnw clean package
```

O JAR será gerado em `target/blog-0.0.1-SNAPSHOT.jar`

### Executar JAR
```bash
java -jar target/blog-0.0.1-SNAPSHOT.jar
```

## 📄 Licença

MIT

---

**Nota:** Certifique-se de que o CouchDB está configurado e acessível antes de iniciar a aplicação. A estrutura esperada no CouchDB inclui uma view `by_date` no design document `posts`.