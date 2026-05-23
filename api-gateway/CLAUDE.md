# api-gateway — Claude Code Context

**Port**: 8080 | **DB**: Redis (rate limiting) | **Status**: Phase 1 — fully implemented

## Responsibility

Single entry point for all external traffic. Handles two cross-cutting concerns:
1. **JWT validation** — verifies RS256 tokens stateless, injects user identity headers downstream
2. **Rate limiting** — per-IP token bucket via Redis, configurable per route

All downstream services trust the headers injected by the gateway and do **not** re-validate tokens.

## Route Map

| Path prefix | Upstream service | Rate limit (req/s) | JWT required |
|-------------|-----------------|-------------------|-------------|
| `/auth/**` | auth-service | none | No |
| `/api/products/**` | product-service | 50 replenish / 100 burst | Yes |
| `/api/orders/**` | order-service | 20 / 50 | Yes |
| `/api/inventory/**` | inventory-service | 30 / 60 | Yes |
| `/api/payments/**` | payment-service | 10 / 20 | Yes |
| `/eureka/web` | discovery-server (direct) | none | No |
| `/v3/api-docs/**` | per-service (rewrite) | none | No |
| `/swagger-ui/**`, `/actuator/**`, `/webjars/**` | — | none | No |

Routes use `lb://service-name` URIs — Spring Cloud Gateway resolves these via Eureka.

## JWT Filter (`JwtAuthFilter`)

`GlobalFilter` with `order = -100` (runs before all other filters).

Flow:
1. Check path against `PUBLIC_PATHS` list — skip filter if public
2. Extract `Authorization: Bearer <token>` header — 401 if missing/malformed
3. Parse + verify RS256 JWT using the `RSAPublicKey` bean
4. Inject `X-User-Id` and `X-User-Role` headers into the forwarded request
5. Downstream services read these headers — they never see the raw JWT

**Why stateless validation**: The gateway holds the RS256 public key (`jwt.public-key` in `application.yml`). It validates tokens locally without calling auth-service, so there's zero per-request latency hit and no single point of failure.

## Adding a Public Endpoint

To make a new path skip JWT validation, add it to `JwtAuthFilter.PUBLIC_PATHS`:
```java
private static final List<String> PUBLIC_PATHS = List.of(
    "/auth/**",
    "/actuator/**",
    // add new path here
    ...
);
```
Prefer adding to the list over adding per-route filters — the global filter is the canonical list.

## Adding a New Route

1. Add the route block to `application.yml` under `spring.cloud.gateway.routes`
2. Use `lb://service-name` (lowercase, matches `spring.application.name` of the target service)
3. Add a `RequestRateLimiter` filter block — copy an existing one and adjust replenish/burst
4. No code changes needed — gateway is purely config-driven

## Key Files

```
filter/JwtAuthFilter.java        — GlobalFilter: JWT parse + header injection + public path bypass
config/JwtPublicKeyConfig.java   — Parses PEM from application.yml → RSAPublicKey bean
ratelimit/IpKeyResolver.java     — KeyResolver bean: extracts client IP for rate limit bucketing
resources/application.yml       — Route table + Redis config + JWT public key
```

## Gotchas

- The gateway runs **WebFlux** (reactive), not Spring MVC. Don't add `spring-boot-starter-web` or
  any blocking library. All filter code must use `Mono`/`Flux` — no blocking calls.
- The RS256 public key in `application.yml` is a **demo key** matching the private key in
  auth-service. If auth-service rotates its key, this file must be updated to match.
- Rate limiting requires Redis to be running. If Redis is down, the `RequestRateLimiter` filter
  throws and requests fail. For development without Redis, remove the filter blocks from routes.
- `IpKeyResolver` reads `X-Forwarded-For` or falls back to remote address. Behind a load balancer,
  ensure `X-Forwarded-For` is set, otherwise all traffic is bucketed under the LB's IP.
