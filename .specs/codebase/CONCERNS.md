# Concerns — Acabou o Mony

## Riscos e Problemas Conhecidos (Impacto Frontend)

| ID | Risco | Impacto Frontend | Severidade | Onde ver |
|----|-------|-----------------|------------|----------|
| C-01 | JWT 15min pode expirar durante checkout longo | UX: usuário precisa re-logar no meio do pagamento | Medium | `specs/user-service/spec.md §4.5` |
| C-02 | Card token MP expira após alguns minutos | UX: formulário preenchido → token inválido → erro | Medium | `docs/teste-r25-webhook.md` |
| C-03 | Refresh token em httpOnly cookie (não acessível via JS) | Frontend não pode ler cookie, só enviar | Info | `specs/user-service/spec.md §4.2` |
| C-04 | API retorna `errors[]` array, não campo único | Frontend precisa iterar erros | Info | `specs/payment-service/spec.md §3.3` |
| C-05 | `X-Merchant-Id` obrigatório em endpoints de pagamento | Cliente CUSTOMER (sem merchant) não pode pagar? | High | `specs/payment-service/spec.md §3.10` |
| C-06 | D-001: Validação de customerId sempre true | Frontend pode passar customerId inválido sem erro | Medium | `docs/sprints/divergencias-dev3.md` |
| C-07 | D-007: Fraud timeout → fallback approve sem análise | Transações aprovadas sem fraude real | High | `docs/sprints/divergencias-dev3.md` |
| C-08 | Rate limit: 100 transações/min por customer | Frontend deve evitar disparos acidentais | Low | `specs/payment-service/spec.md §3.4` |

## Áreas que Precisam de Atenção Extra

| Área | Motivo | Ação recomendada |
|------|--------|-----------------|
| `C-05`: Merchant ID no fluxo CUSTOMER | CUSTOMER não tem `merchantId` no JWT, mas payment-service exige `X-Merchant-Id` | Verificar se o frontend deve passar o `merchantId` do lojista (do pedido), não do customer |
| `C-03`: Refresh token cookie | Cookie httpOnly não pode ser lido por JS. O endpoint `/auth/refresh` lê o cookie automaticamente | Frontend chama `/auth/refresh` sem body, só com `credentials: 'include'` |
| `C-02`: Card token expirado | O token MP tem validade curta (~30min). Se o usuário demora no formulário, o token pode expirar | Gerar card token apenas no momento do submit, não ao preencher o formulário |

## Dívida Técnica (Backend)

| Item | Onde | Impacto |
|------|------|---------|
| FraudeServiceClient timeout curto (250ms) | `payment-service` | Fallback sem análise real |
| `GET /internal/users/{id}` ausente | `user-service` | Validação de customer sempre true |
| `USER_SERVICE_URL` faltando no docker-compose | `payment-service` | Pode quebrar em rebuild |
| Header role singular/plural | `api-gateway` vs `payment-service` | Possível 403 |
