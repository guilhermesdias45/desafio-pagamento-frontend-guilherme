# Security Checklist for Code Review

## Authentication
- [ ] Strong password policy enforced (min 12 chars, complexity)
- [ ] MFA supported/required for sensitive operations
- [ ] Session tokens: secure, httpOnly, sameSite, short expiry
- [ ] JWT: RS256/ES256, short expiry, refresh rotation, revocation
- [ ] No auth bypasses (debug endpoints, default creds)
- [ ] Brute force protection (rate limit, lockout, CAPTCHA)
- [ ] Password reset: time-limited, single-use, secure tokens

## Authorization
- [ ] RBAC/ABAC enforced on ALL endpoints
- [ ] Resource-level permissions (not just role-based)
- [ ] No IDOR (Insecure Direct Object References)
- [ ] Admin functions separated, audited
- [ ] Principle of least privilege in service accounts

## Input Validation
- [ ] Allow-list validation on all inputs
- [ ] Content-Type enforced
- [ ] Request size limits
- [ ] File upload: type, size, malware scan, storage isolation
- [ ] No eval/exec/deserialization of user input

## Output Encoding
- [ ] HTML: context-aware encoding (attribute, JS, CSS, URL)
- [ ] JSON: proper Content-Type, no JSONP
- [ ] CSP header restrictive (no unsafe-inline/eval)
- [ ] X-Frame-Options: DENY/SAMEORIGIN

## Injection Prevention
- [ ] SQL: Parameterized queries ONLY (no string concat)
- [ ] NoSQL: Parameterized/validated
- [ ] LDAP: Escaped inputs
- [ ] Command: No shell execution, or strict allow-list
- [ ] XPath/XQuery: Parameterized

## Cryptography
- [ ] TLS 1.2+ everywhere (enforced via HSTS)
- [ ] Certificates: valid, pinned where critical
- [ ] Passwords: Argon2id/bcrypt/scrypt (not SHA/MD5)
- [ ] Keys: Generated securely, rotated, stored in vault
- [ ] No custom crypto implementations
- [ ] Sensitive data encrypted at rest (AES-256-GCM)

## Logging & Monitoring
- [ ] No secrets in logs (passwords, tokens, PII, cards)
- [ ] Structured logging (JSON) for SIEM
- [ ] Audit trail: who, what, when, where, result
- [ ] Alert on: auth failures, privilege changes, anomalies
- [ ] Log integrity (append-only, tamper-evident)

## Dependencies
- [ ] SCA scanning in CI (OWASP Dependency Check, Snyk, etc.)
- [ ] No known CVEs in production deps
- [ ] Pin versions, verify integrity (lockfiles, checksums)
- [ ] Minimal dependency surface
- [ ] License compliance

## Infrastructure
- [ ] Containers: non-root, read-only FS, distroless/scratch
- [ ] Network: least privilege (security groups, network policies)
- [ ] Secrets: Vault/Sealed Secrets (not env vars in repo)
- [ ] Backup: encrypted, tested restore, offsite
- [ ] Disaster recovery: RPO/RTO defined, tested

## Compliance (Context-Dependent)
- [ ] PCI DSS: Card data never logged, tokenized, segmented
- [ ] LGPD/GDPR: Lawful basis, consent, DSR workflow, DPIA
- [ ] HIPAA: BAA, encryption, access logging, breach notification
- [ ] SOX: Change control, segregation of duties, audit trail

## API Security
- [ ] Rate limiting per client/endpoint
- [ ] API versioning in URL/header
- [ ] Deprecation policy communicated
- [ ] Request/response validation (OpenAPI schema)
- [ ] Idempotency keys for mutating operations
- [ ] Webhook signature verification

## Testing
- [ ] SAST in CI (CodeQL, Semgrep, SonarQube)
- [ ] DAST for running apps (OWASP ZAP)
- [ ] Dependency scanning (SCA)
- [ ] Secret scanning (TruffleHog, GitLeaks)
- [ ] Penetration testing (annual + major changes)
- [ ] Security regression tests for fixed vulns