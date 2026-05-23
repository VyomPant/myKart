# discovery-server — Claude Code Context

**Port**: 8761 | **DB**: none | **Status**: Phase 1 — fully implemented

## Responsibility

Eureka service registry. Every other service registers here at startup and polls for
peer addresses. The gateway uses it for `lb://service-name` URL resolution.

This service has **no business logic** — it is purely infrastructure. Do not add
controllers, DB dependencies, or Kafka to it.

## Configuration

```yaml
eureka:
  client:
    register-with-eureka: false   # doesn't register itself
    fetch-registry: false         # doesn't fetch its own registry
  server:
    wait-time-in-ms-when-sync-empty: 0  # speeds up startup in dev (no 5s wait)
```

`wait-time-in-ms-when-sync-empty: 0` is intentional for local development. In a
multi-node production setup, this should be removed to allow peer sync time.

## Dashboard

The Eureka dashboard is available at `http://localhost:8761` directly, or via
the gateway at `http://localhost:8080/eureka/web`.

## How Services Register

Every other service has this in its `application.yml`:
```yaml
eureka:
  client:
    service-url:
      defaultZone: http://${EUREKA_HOST:localhost}:8761/eureka/
  instance:
    prefer-ip-address: true
```

`prefer-ip-address: true` means services register their container/pod IP rather
than hostname — required in Docker/Kubernetes where hostnames are not resolvable
across containers.

## Run Order Dependency

**discovery-server must be the first service started.** Other services retry
registration on a backoff schedule, but the gateway will fail requests if the
registry is empty when it tries to resolve `lb://service-name`.

Start order: `docker-compose up -d` → `discovery-server` → everything else.

## Key Files

```
DiscoveryServerApplication.java   — @EnableEurekaServer, nothing else
resources/application.yml         — Port 8761, self-exclusion config
```

## Gotchas

- Self-registration is disabled (`register-with-eureka: false`). Adding
  `@EnableDiscoveryClient` here would cause the server to try to register with itself.
- If you see `EMERGENCY! EUREKA MAY BE INCORRECTLY CLAIMING INSTANCES ARE UP` in logs,
  this is Eureka's self-preservation mode firing because not enough heartbeats are received
  (common when running < 3 services locally). It is a warning, not an error.
