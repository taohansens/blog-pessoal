# Configuração de Autenticação GitHub OAuth2

Este documento explica como configurar a autenticação OAuth2 com GitHub para proteger as operações de escrita (POST, PUT, DELETE) da API.

## 1. Criar OAuth App no GitHub

1. Acesse: https://github.com/settings/developers
2. Clique em "New OAuth App"
3. Preencha os campos:
   - **Application name**: Blog Pessoal (ou qualquer nome)
   - **Homepage URL**: `http://localhost:9899` (ou sua URL de produção)
   - **Authorization callback URL**: `http://localhost:9899/login/oauth2/code/github` (ou sua URL de produção)
4. Clique em "Register application"
5. **Copie o Client ID e Client Secret** gerados

## 2. Configurar Variáveis de Ambiente

Configure as seguintes variáveis de ambiente:

```bash
# Email do administrador (único que pode criar/editar/deletar posts)
export ADMIN_EMAIL=seu-email@example.com

# Credenciais OAuth2 do GitHub
export GITHUB_CLIENT_ID=seu-client-id
export GITHUB_CLIENT_SECRET=seu-client-secret
```

Ou adicione no arquivo `application.yml`:

```yaml
app:
  admin:
    email: seu-email@example.com

spring:
  security:
    oauth2:
      client:
        registration:
          github:
            client-id: seu-client-id
            client-secret: seu-client-secret
```

## 3. Como Funciona

### Endpoints Públicos (sem autenticação)
- `GET /api/posts/all` - Listar todos os posts
- `GET /api/posts/{slug}` - Buscar post por slug
- `GET /api/posts?page={page}&size={size}` - Listar posts paginados

### Endpoints Protegidos (requerem autenticação como administrador)
- `POST /api/posts` - Criar post
- `PUT /api/posts/{id}` - Atualizar post
- `DELETE /api/posts/{id}` - Deletar post

## 4. Fluxo de Autenticação

1. **Acesse o endpoint de login**: `http://localhost:9899/oauth2/authorization/github`
2. Você será redirecionado para o GitHub para autorizar
3. Após autorizar, você será redirecionado de volta para a aplicação
4. Agora você está autenticado e pode fazer operações de escrita

## 5. Verificação de Administrador

O sistema verifica se o email do usuário autenticado corresponde ao email configurado em `ADMIN_EMAIL`. 

**Importante**: 
- O GitHub pode não retornar o email público se você não tiver configurado
- Neste caso, o sistema usa o `login` (username) do GitHub
- Certifique-se de que o email configurado em `ADMIN_EMAIL` corresponde ao email público do GitHub OU ao username

## 6. Testando a Autenticação

### Teste de Leitura (público)
```bash
curl http://localhost:9899/api/posts/all
```

### Teste de Escrita (requer autenticação)
Para testar operações de escrita, você precisa:
1. Autenticar via navegador: `http://localhost:9899/oauth2/authorization/github`
2. Usar cookies de sessão nas requisições

Ou usar um cliente HTTP que suporte OAuth2 (como Postman, Insomnia, etc.)

## 7. Produção

Para produção, certifique-se de:
1. Atualizar a "Authorization callback URL" no GitHub OAuth App para sua URL de produção
2. Configurar as variáveis de ambiente no servidor
3. Usar HTTPS (OAuth2 requer HTTPS em produção)
4. Configurar sessões seguras (cookies HttpOnly, Secure, SameSite)

## 8. Troubleshooting

### Erro: "Acesso não autorizado"
- Verifique se você está autenticado
- Verifique se o email corresponde ao `ADMIN_EMAIL` configurado

### Erro: "Email não encontrado"
- O GitHub pode não retornar o email público
- Configure o email público no GitHub: Settings > Emails > Public email
- Ou use o username do GitHub como `ADMIN_EMAIL`

### Erro: "Invalid client credentials"
- Verifique se o `GITHUB_CLIENT_ID` e `GITHUB_CLIENT_SECRET` estão corretos
- Verifique se a "Authorization callback URL" está correta no GitHub

