# auth-service — Claude Code Context

**Port**: 8082 | **DB**: PostgreSQL (`auth_db`) | **Status**: Phase 1 — fully implemented

## Responsibility

The ONLY service that issues JWT tokens. Owns user identity and credentials.
The gateway validates tokens stateless using the RS256 public key — no auth-service call per request.

## Implemented Endpoints

```
POST /auth/register  → creates user (BUYER or SELLER), returns UserResponse
POST /auth/login     → validates credentials, returns {accessToken, refreshToken}
POST /auth/refresh   → exchanges refreshToken for new accessToken (rotates token)
POST /auth/logout    → revokes the provided refreshToken
GET  /auth/me        → validates JWT, returns current user info (requires Bearer token)
```

All endpoints are prefixed with `/auth/`. The gateway routes these WITHOUT JWT validation
— the service handles its own auth for `/auth/me`.

## JWT Design

- **Algorithm**: RS256 (asymmetric)
- **Private key**: stored in `application.yml` (demo) — in prod, use secrets manager
- **Public key**: DERIVED from private key at startup in `JwtConfig.java` — no duplication
- **Payload**: `{"sub": "<userId>", "role": "BUYER|SELLER", "iat": ..., "exp": ...}`
- **Access token TTL**: 1 hour (`auth.jwt.access-token-expiration-ms=3600000`)
- **Refresh token TTL**: 7 days (`auth.jwt.refresh-token-expiration-ms=604800000`)

## Token Storage

Refresh tokens are stored hashed (SHA-256) in `refresh_tokens` table.
On logout: token is marked `revoked=true` (not deleted — preserves audit trail).
On refresh: old token is revoked, new token issued atomically in `@Transactional`.

## Security Config

- `/auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout` → `permitAll()`
- `/auth/me` → requires `authenticated()` (JWT validated by `JwtAuthFilter`)
- Spring Security session: `STATELESS`

## Key Files

```
config/
  JwtConfig.java         — parses PEM private key, derives public key bean
  SecurityConfig.java    — Spring Security filter chain
  OpenApiConfig.java     — Springdoc bearerAuth scheme
service/
  JwtService.java        — generateAccessToken(), validateToken()
  AuthService.java       — register/login/refresh/logout/getCurrentUser business logic
security/
  JwtAuthFilter.java     — OncePerRequestFilter, populates SecurityContext for /auth/me
```

## Database Migrations

```
V1__create_users_table.sql         — users (id, email, password_hash, role, created_at)
V2__create_refresh_tokens_table.sql — refresh_tokens (id, user_id, token_hash, expires_at, revoked)
```

Schema is managed by Flyway. `spring.jpa.hibernate.ddl-auto=validate`.

## Common Gotchas

- The `User` entity implements `UserDetailsService` — `getPassword()` returns `passwordHash`
- `JwtAuthFilter` in auth-service is different from the gateway's `JwtAuthFilter`
  (both validate JWT but auth-service's populates the Spring SecurityContext)
- `PasswordEncoder` bean must be declared in `SecurityConfig` — not in `AuthService` (circular bean)
- The `role` column uses a PostgreSQL ENUM type (`user_role`) — matches `@Column(columnDefinition = "user_role")`
