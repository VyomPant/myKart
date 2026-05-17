# /bootstrap-service

Scaffold a new Spring Boot microservice module in the myKart multi-module Maven project.

## Usage

```
/bootstrap-service <service-name>
```

Example: `/bootstrap-service notification-service`

## What this command does

1. Creates `<service-name>/pom.xml` inheriting from the parent POM with standard dependencies:
   - `spring-boot-starter-web` (or `spring-boot-starter` for headless services)
   - `spring-cloud-starter-netflix-eureka-client`
   - `spring-boot-starter-actuator`
   - `micrometer-registry-prometheus`
   - `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`
   - `net.logstash.logback:logstash-logback-encoder`
   - `mkart-common`

2. Creates the package structure: `com.mykart.<servicename>`

3. Creates `<service-name>/src/main/java/com/mykart/<name>/Application.java`

4. Creates `<service-name>/src/main/resources/application.yml` with:
   - `spring.application.name: <service-name>`
   - Eureka client config pointing to `${EUREKA_HOST:localhost}:8761`
   - Actuator endpoints: health, info, prometheus
   - OTel exporter to `${OTEL_HOST:localhost}:4317`
   - Structured logging config reference

5. Creates `<service-name>/src/main/resources/logback-spring.xml` using `LogstashEncoder`

6. Creates `<service-name>/CLAUDE.md` with a template for service-specific context

7. Adds the module to the parent `pom.xml` `<modules>` section

8. Adds a scrape target to `infra/prometheus/prometheus.yml`

## Conventions to follow

- Package: `com.mykart.<lowercasename>`
- Main class: `<PascalCaseName>Application`
- Port: assign the next unused port from the port registry in the root CLAUDE.md
- Always register with Eureka, expose actuator prometheus endpoint, configure OTel
