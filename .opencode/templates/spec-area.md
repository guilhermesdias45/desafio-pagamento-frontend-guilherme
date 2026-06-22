# Spec: [Area Name]

**ID:** SPEC-FE-[AREA]-[NN]
**Área:** [area-slug]
**Status:** Draft

---

## 1. Visão Geral

O que esta área do frontend faz. 2-3 frases.

## 2. User Stories

### P1: [Story Title] ⭐ MVP

**User Story:** As a [role], I want [capability] so that [benefit].

**Acceptance Criteria:**
1. WHEN [user action] THEN system SHALL [expected behavior]
2. WHEN [edge case] THEN system SHALL [graceful handling]

**Independent Test:** [How to verify this in isolation]

## 3. Interface Types (Typescript)

```typescript
// Interfaces que esta área expõe para outras áreas
export interface I[AreaName]Service {
  // métodos
}

// Tipos de dados
export type [TypeName] = {
  // campos
};
```

## 4. API Endpoints Consumidos

| Endpoint | Método | Headers | Request | Response |
|----------|--------|---------|---------|----------|
| ... | ... | ... | ... | ... |

## 5. Mock Contracts

```typescript
// Interface que esta área MOCKA se depender de outra área
// (preenchido pelo Coder se necessário)
export interface IMockDependency {
  // métodos que serão mockados
}
```

## 6. Error Scenarios

| Erro | Código | Comportamento esperado |
|------|--------|----------------------|
| ... | ... | ... |

## 7. Requirement Traceability

| ID | Story | Status |
|----|-------|--------|
| [AREA]-01 | P1: ... | Pending |
