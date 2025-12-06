# Docker Setup

Este projeto inclui configuração Docker para facilitar o desenvolvimento e deploy.

## Arquivos

- `Dockerfile`: Build multi-stage da aplicação Spring Boot
- `docker-compose.yml`: Orquestração completa com API e CouchDB
- `.dockerignore`: Arquivos excluídos do build

## Variáveis de Ambiente

Crie um arquivo `.env` na raiz do projeto (ou use variáveis de ambiente do sistema):

```bash
# CouchDB Configuration
COUCHDB_URI=http://couchdb:5984
COUCHDB_USER=admin
COUCHDB_PASSWORD=admin
COUCHDB_DB_NAME=blog

# CORS Configuration
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:8080,https://meusite.com

# Java Options (opcional)
JAVA_OPTS=-Xmx512m -Xms256m
```

## Como Usar

### Build e Start

```bash
# Build e inicia todos os serviços
docker-compose up -d --build

# Ver logs
docker-compose logs -f blog-api

# Parar serviços
docker-compose down

# Parar e remover volumes (apaga dados do CouchDB)
docker-compose down -v
```

### Apenas Build da Imagem

```bash
docker build -t blog-api:latest .
```

### Usar JAR já Buildado

Se você já tem o JAR buildado localmente, pode modificar o `docker-compose.yml` para usar uma imagem base e copiar o JAR:

```yaml
blog-api:
  image: eclipse-temurin:21-jre-alpine
  volumes:
    - ./target/*.jar:/app/app.jar
  # ... resto da configuração
```

## Portas

- **API**: `9899`
- **CouchDB**: `5984`

## Acessos

- API: http://localhost:9899
- CouchDB Fauxton UI: http://localhost:5984/_utils

## Notas

- O CouchDB precisa ser inicializado manualmente após o primeiro start
- Os dados do CouchDB são persistidos no volume `couchdb-data`
- A aplicação aguarda o CouchDB estar pronto antes de iniciar (via `depends_on`)



