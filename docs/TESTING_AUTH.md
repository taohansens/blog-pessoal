# Guia de Testes de Autenticação

Este documento explica como testar a autenticação OAuth2 com GitHub e a verificação de administrador.

## Pré-requisitos

1. **Configurar variáveis de ambiente ou application.yml:**
   ```bash
   export ADMIN_EMAIL=seu-email@example.com
   export GITHUB_CLIENT_ID=seu-client-id
   export GITHUB_CLIENT_SECRET=seu-client-secret
   ```

2. **Iniciar a aplicação:**
   ```bash
   mvn spring-boot:run
   ```

## 1. Testar Endpoints Públicos (GET) - Sem Autenticação

Estes endpoints devem funcionar **sem autenticação**:

### Listar todos os posts
```bash
curl http://localhost:9899/api/posts/all
```

### Buscar post por slug
```bash
curl http://localhost:9899/api/posts/meu-post-slug
```

### Listar posts paginados
```bash
curl http://localhost:9899/api/posts?page=0&size=10
```

**Resultado esperado:** Status 200 OK com dados dos posts

## 2. Testar Endpoints Protegidos (POST, PUT, DELETE) - Requerem Autenticação

Estes endpoints devem retornar **401 Unauthorized** sem autenticação:

### Tentar criar post sem autenticação
```bash
curl -X POST http://localhost:9899/api/posts \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Teste",
    "content": "Conteúdo de teste"
  }'
```

**Resultado esperado:** Status 401 Unauthorized

### Tentar atualizar post sem autenticação
```bash
curl -X PUT http://localhost:9899/api/posts/algum-id \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Título Atualizado",
    "content": "Conteúdo atualizado"
  }'
```

**Resultado esperado:** Status 401 Unauthorized

### Tentar deletar post sem autenticação
```bash
curl -X DELETE http://localhost:9899/api/posts/algum-id
```

**Resultado esperado:** Status 401 Unauthorized

## 3. Autenticar via Navegador

### Passo 1: Acessar endpoint de login
Abra no navegador:
```
http://localhost:9899/oauth2/authorization/github
```

### Passo 2: Autorizar no GitHub
- Você será redirecionado para o GitHub
- Faça login se necessário
- Autorize a aplicação

### Passo 3: Verificar redirecionamento
- Após autorizar, você será redirecionado de volta
- O navegador terá cookies de sessão com a autenticação

## 4. Testar com Autenticação (via Navegador + Cookies)

Após autenticar no navegador, você pode usar as ferramentas de desenvolvedor para fazer requisições:

### No Chrome/Edge (F12 > Network)
1. Autentique via navegador primeiro
2. Abra o DevTools (F12)
3. Vá para a aba Network
4. Faça uma requisição POST/PUT/DELETE
5. Os cookies de sessão serão enviados automaticamente

### Usando curl com cookies salvos

**Passo 1:** Autenticar e salvar cookies
```bash
# Autenticar e salvar cookies
curl -c cookies.txt -L "http://localhost:9899/oauth2/authorization/github"
```

**Nota:** Isso pode não funcionar completamente porque requer interação com o GitHub. É melhor usar o navegador.

**Passo 2:** Usar cookies salvos
```bash
curl -b cookies.txt -X POST http://localhost:9899/api/posts \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Post de Teste",
    "content": "Conteúdo do post de teste"
  }'
```

## 5. Testar Verificação de Administrador

### Cenário 1: Usuário Administrador (email correto)

1. Configure `ADMIN_EMAIL` com seu email do GitHub
2. Autentique via navegador
3. Tente criar um post:
   ```bash
   # Via navegador DevTools ou Postman com cookies
   POST http://localhost:9899/api/posts
   {
     "title": "Meu Post",
     "content": "Conteúdo"
   }
   ```

**Resultado esperado:** Status 201 Created com o post criado

### Cenário 2: Usuário Não-Administrador (email diferente)

1. Configure `ADMIN_EMAIL` com um email diferente do seu
2. Autentique via navegador com sua conta GitHub
3. Tente criar um post

**Resultado esperado:** Status 403 Forbidden

## 6. Verificar Logs da Aplicação

Os logs mostrarão informações sobre autenticação:

### Logs de sucesso (administrador):
```
DEBUG - Acesso autorizado para administrador: POST /api/posts
INFO - Post criado com sucesso: Meu Post (ID: ...)
```

### Logs de falha (não autenticado):
```
WARN - Tentativa de acesso não autenticado: POST /api/posts
```

### Logs de falha (não autorizado):
```
WARN - Tentativa de acesso não autorizado: POST /api/posts por usuário: outro-email@example.com
```

## 7. Testar com Postman/Insomnia

### Configuração:
1. **Autenticação:** OAuth 2.0
2. **Grant Type:** Authorization Code
3. **Authorization URL:** `http://localhost:9899/oauth2/authorization/github`
4. **Access Token URL:** (gerenciado pelo Spring Security)
5. **Client ID:** Seu `GITHUB_CLIENT_ID`
6. **Client Secret:** Seu `GITHUB_CLIENT_SECRET`

### Ou usar Session/Cookies:
1. Autentique via navegador primeiro
2. Copie os cookies de sessão
3. Adicione os cookies nas requisições do Postman

## 8. Checklist de Testes

- [ ] GET `/api/posts/all` funciona sem autenticação
- [ ] GET `/api/posts/{slug}` funciona sem autenticação
- [ ] POST `/api/posts` retorna 401 sem autenticação
- [ ] PUT `/api/posts/{id}` retorna 401 sem autenticação
- [ ] DELETE `/api/posts/{id}` retorna 401 sem autenticação
- [ ] Autenticação via navegador funciona
- [ ] POST `/api/posts` funciona com autenticação de administrador
- [ ] POST `/api/posts` retorna 403 com autenticação de não-administrador
- [ ] Logs mostram tentativas de acesso corretamente

## 9. Troubleshooting

### Problema: Sempre retorna 401
**Solução:**
- Verifique se você autenticou via navegador primeiro
- Verifique se os cookies de sessão estão sendo enviados
- Verifique se o OAuth2 está configurado corretamente no `application.yml`

### Problema: Sempre retorna 403 (mesmo sendo admin)
**Solução:**
- Verifique se o `ADMIN_EMAIL` está configurado corretamente
- Verifique se o email do GitHub corresponde ao `ADMIN_EMAIL`
- Verifique os logs para ver qual email está sendo verificado
- Lembre-se: GitHub pode retornar `login` ao invés de `email` se o email não for público

### Problema: Não consigo autenticar
**Solução:**
- Verifique se `GITHUB_CLIENT_ID` e `GITHUB_CLIENT_SECRET` estão corretos
- Verifique se a "Authorization callback URL" no GitHub OAuth App está correta: `http://localhost:9899/login/oauth2/code/github`
- Verifique os logs da aplicação para erros

### Problema: Email não encontrado
**Solução:**
- Configure o email público no GitHub: Settings > Emails > Public email
- Ou use o `login` (username) do GitHub como `ADMIN_EMAIL`

## 10. Exemplo Completo de Teste

```bash
# 1. Testar endpoint público (deve funcionar)
curl http://localhost:9899/api/posts/all

# 2. Testar endpoint protegido sem auth (deve retornar 401)
curl -X POST http://localhost:9899/api/posts \
  -H "Content-Type: application/json" \
  -d '{"title":"Teste","content":"Teste"}'

# 3. Autenticar via navegador
# Abra: http://localhost:9899/oauth2/authorization/github

# 4. Após autenticar, usar DevTools do navegador para testar POST/PUT/DELETE
# Ou usar Postman/Insomnia com cookies de sessão
```

## 11. Verificar Email do Usuário Autenticado

Para debugar, você pode adicionar um endpoint temporário para verificar o email:

```java
@GetMapping("/api/auth/me")
public Mono<ResponseEntity<Map<String, String>>> getCurrentUser(
        Authentication authentication) {
    if (authentication instanceof OAuth2AuthenticationToken token) {
        OAuth2User user = token.getPrincipal();
        String email = (String) user.getAttributes().get("email");
        String login = (String) user.getAttributes().get("login");
        
        return Mono.just(ResponseEntity.ok(Map.of(
            "email", email != null ? email : "null",
            "login", login != null ? login : "null"
        )));
    }
    return Mono.just(ResponseEntity.status(401).build());
}
```

Acesse: `http://localhost:9899/api/auth/me` após autenticar para ver qual email/login está sendo usado.

