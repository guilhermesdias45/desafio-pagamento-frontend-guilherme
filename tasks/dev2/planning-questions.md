# Planning Questions — User Service (dev2)

## Codebase Summary

Auditoria do estado atual antes de propor as perguntas:

- **`services/user-service/`** existe como esqueleto: `pom.xml` completo (Java 21, Spring Boot 3.4.5, JJWT 0.12.6, totp 1.7.1, Testcontainers, Lombok+MapStruct, JaCoCo com `minimum=0.90` em `BUNDLE`), `application.yml` com Virtual Threads + Flyway + Redis + jwt.* + security.login.*, `application-docker.yml` com env vars do Docker, Dockerfile multi-stage não-root. **Nenhum código Java** além de `UserServiceApplication.java` (apenas `@SpringBootApplication + @EnableKafka + @EnableAsync`).
- **`services/fraud-service/`** e **`services/order-service/`** também são esqueletos vazios — **não existem padrões de Controller/Service/DTO ainda no monorepo**. O user-service vai *estabelecer* os padrões para os outros, não consumi-los. Isso é importante: as escolhas feitas aqui viram convenção do repo.
- **Flyway**: configurado em `application.yml` (`classpath:db/migration`), mas pasta `src/main/resources/db/migration/` **não existe**. Hibernate em `ddl-auto: validate` — qualquer entidade JPA sem migration correspondente quebra o boot.
- **Infra (`docker-compose.yml`)**: PostgreSQL 16, Redis 7 (com password obrigatória — `requirepass`), Kafka KRaft 3.7. O `kafka-init` job cria os tópicos `user.registered`, `user.login.success`, `user.login.blocked`, `user.2fa.enabled` como **tópicos separados**, enquanto `specs/user-service/plan.md` (linhas 52-58) descreve um **tópico único `user-events`** com tipo no payload. **Inconsistência real.**
- **`.env.example`** já contém `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY` e `AES_SECRET_KEY`. **Não contém `INTERNAL_SECRET` nem `TOTP_AES_KEY` nem `TOTP_ISSUER`**, que `plan.md` (linhas 226-233) lista como obrigatórios. Também: `plan.md` chama de `TOTP_AES_KEY` mas o docker-compose passa `AES_SECRET_KEY` para o user-service — possível duplicação semântica.
- **Database**: `scripts/init-databases.sql` cria `user_db`, mas o serviço usa `ddl-auto: validate`, então sem migrations Flyway o app não sobe. As 4 migrations (V1..V4) precisam existir antes de qualquer teste de integração.
- **`specs/user-service/tasks.md`** já lista 33 tarefas em ordem sequencial com pares TDD (RED → GREEN). Meu trabalho aqui é reorganizar isso em **waves** para execução paralela por múltiplos sub-agents, identificando o que pode rodar em paralelo dentro de cada wave.
- **PRD afirma** "fraud-service como reference implementation" — isso é falso no estado atual; o prompt do orquestrador parece desatualizado.

---

## Questions

### Q1: Tópicos Kafka — único vs múltiplos
**Context:** `plan.md` (linhas 52-58) descreve um tópico único `user-events` com o tipo do evento no payload. Mas `docker-compose.yml` (linhas 105-108) já cria 4 tópicos separados: `user.registered`, `user.login.success`, `user.login.blocked`, `user.2fa.enabled`. Os dois designs são incompatíveis — `UserEventProducer` precisa escolher um.
**Question:** Qual padrão devo seguir?
**Options:**
- A) **Manter os 4 tópicos separados** (alinhar com docker-compose) — atualizo o plan.md durante o GENERATE; `UserEventProducer` publica em tópicos distintos por tipo
- B) **Tópico único `user-events`** (alinhar com plan.md) — preciso de uma task para alterar `docker-compose.yml` (remover os 4 tópicos, criar `user-events`) e o `kafka-init`; `UserEventProducer` discrimina por header/payload
- C) Outra abordagem — você define

### Q2: Variáveis de ambiente — INTERNAL_SECRET, TOTP_AES_KEY, TOTP_ISSUER
**Context:** `plan.md` define 3 env vars que **não existem** no `.env.example` atual:
- `INTERNAL_SECRET` — usada pelo `InternalSecretFilter` e compartilhada com api-gateway
- `TOTP_AES_KEY` — chave AES-256-GCM para criptografar o secret TOTP no banco
- `TOTP_ISSUER` — nome exibido no Google Authenticator (default `AcabouoMony`)

O `.env.example` já tem `AES_SECRET_KEY` (genérica) e o `docker-compose.yml` linha 179 passa exatamente essa env como `AES_SECRET_KEY` para o user-service. **Conflito:** uso `AES_SECRET_KEY` (já existente) ou crio `TOTP_AES_KEY` (renomeio o uso)?

**Question:** Como tratar essas 3 env vars?
**Options:**
- A) **Reutilizar `AES_SECRET_KEY` existente** para o TOTP secret (não duplica); adicionar apenas `INTERNAL_SECRET` e `TOTP_ISSUER` ao `.env.example` e `docker-compose.yml`
- B) **Renomear `AES_SECRET_KEY` → `TOTP_AES_KEY`** no `.env.example` e no `docker-compose.yml` (alinha com plan.md mas quebra naming já em uso)
- C) **Manter `AES_SECRET_KEY` para genérico e adicionar `TOTP_AES_KEY` separado** (over-engineering por enquanto, mas separação semântica clara)

### Q3: Dependência circular V1/V2 Flyway — confirmar estratégia
**Context:** `plan.md` (linha 196-199) propõe resolver a dependência circular (`users.merchant_id → merchants`, `merchants.owner_id → users`) com:
1. V1 cria `users` sem FK para `merchants`
2. V2 cria `merchants` com FK `owner_id → users`
3. V2 termina com `ALTER TABLE users ADD CONSTRAINT fk_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id)`

Isso significa que **V1 e V2 estão acoplados** — V1 sozinha deixa o schema em estado válido mas incompleto. Tasks "Flyway V1" e "Flyway V2" não podem rodar em paralelo (V2 depende do estado deixado por V1).

**Question:** Confirma essa estratégia (V1 → V2 com ALTER no final, serializado) ou prefere alternativa?
**Options:**
- A) **Manter como plan.md descreve** — V1 e V2 são uma wave serial; tasks subsequentes (V3, V4) podem rodar em paralelo com features após V2
- B) **Fundir V1 e V2 em uma migration única** `V1__create_users_and_merchants.sql` — elimina o ALTER, mas perde a separação semântica
- C) **`merchant_id` em users vira `merchant_id UUID` simples (sem FK)** — desacopla migrations, mas perde integridade referencial

### Q4: Wave structure — granularidade desejada
**Context:** `specs/user-service/tasks.md` lista 33 tarefas; cada par TDD (RED + GREEN) está em 2 tasks separadas. Em modo TDD obrigatório, **uma task pode conter tanto o teste RED quanto o GREEN** (o agente implementador escreve o teste, vê falhar, depois implementa), o que reduz para ~20 tasks executáveis. Alternativamente posso manter o par separado (RED task em wave anterior à GREEN task, mas isso obriga handoff entre agents).

**Question:** Como você quer ver as tarefas estruturadas?
**Options:**
- A) **Uma task por unidade lógica** (ex: "AuthService.register com TDD completo" — RED + GREEN + REFACTOR dentro de uma task). Mais natural para um agente; menos arquivos.
- B) **Pares RED/GREEN separados** (task-11 = testes falhando de register; task-12 = implementação). Forçar handoff entre agents; pode dar problema de contexto.
- C) **Híbrido**: domínio/infra (entidades, migrations) sem par TDD; lógica de negócio (services, controllers) com RED separado.

### Q5: Paralelismo de testes de integração com Testcontainers
**Context:** Testcontainers sobe containers reais (PostgreSQL + Redis + Kafka). Se 4 agents rodam testes de integração em paralelo, podem subir 4 conjuntos de containers — alto consumo de recursos e contenção de portas. Padrão do repo (a ser definido) precisa decidir entre containers compartilhados via Singleton ou por classe de teste.

**Question:** O Testcontainers deve ser configurado como Singleton compartilhado entre todos os testes de integração, ou por classe (`@Testcontainers` em cada uma)?
**Options:**
- A) **Singleton via `@Container static`** ou abstract base class — containers sobem uma vez por JVM; testes paralelos compartilham; mais rápido e padrão da indústria
- B) **Por classe de teste** — isolamento total, mais lento, mais recursos
- C) Decidir caso a caso (não recomendado — gera inconsistência)

### Q6: PATCH /me — escopo Sprint 1
**Context:** `plan.md` confirma: apenas `fullName` é editável no Sprint 1. PRD reforça. Mas o `tasks.md` original tem isso em uma task separada (#25 controllers).

**Question:** PATCH /me com apenas fullName deve virar uma task isolada ou ficar agrupada com `UserController` (perfil GET + PATCH)?
**Options:**
- A) **Agrupar em `UserController`** — GET /me + PATCH /me na mesma task (ambos só envolvem fullName/perfil básico)
- B) **Task separada** — facilita rollback se algo der errado, mas é overhead

### Q7: Validação condicional de RegisterRequest (MERCHANT_OWNER)
**Context:** `RegisterRequest` é um Record com campos `companyName` e `cnpj` que são **obrigatórios apenas quando `role == MERCHANT_OWNER`**. Bean Validation nativo (anotações em campos) não suporta isso facilmente. Opções:
- (a) `@AssertTrue` em método derivado do Record
- (b) Custom validator class-level (`@ValidRegisterRequest`)
- (c) Validação programática no `AuthService.register()` antes do BCrypt

**Question:** Qual abordagem você prefere?
**Options:**
- A) **Class-level custom validator** (`@ValidRegisterRequest` no record) — declarativo, testável isoladamente, padrão Bean Validation
- B) **Validação programática no service** — sem framework annotation, lógica explícita
- C) **`@AssertTrue` em método do Record** — minimalista, mas mistura validação com domínio

### Q8: TDD mode para este build
**Context:** A regra do projeto (CLAUDE.md + PRD) é TDD obrigatório. Pergunta padrão de confirmação.

**Question:** Confirma que TDD mode está ativo para todas as tarefas funcionais (cada task deve escrever RED falhando antes de GREEN)?
**Options:**
- A) **Sim** — toda task funcional inclui seção "## TDD Mode" com RED → GREEN → REFACTOR; cada CE-* da spec vira pelo menos 1 teste
- B) **TDD apenas para lógica de negócio** (AuthService, TwoFactorService) — infra (migrations, configs, DTOs) sem ciclo TDD formal
- C) Não (não recomendado, contradiz CLAUDE.md)
