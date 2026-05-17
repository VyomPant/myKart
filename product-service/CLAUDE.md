# product-service — Claude Code Context

**Port**: 8083 | **DB**: MongoDB (`product_db`) | **Status**: Phase 2

## Responsibility

Product catalog. Sellers create and manage products. Buyers browse and search.
Does NOT handle inventory — stock is managed by inventory-service via skuCode.

## Implemented Endpoints

```
POST   /api/products            → create product (SELLER, via X-User-Role header)
GET    /api/products/{id}       → get by ID (no auth)
GET    /api/products?page&size  → paginated list (no auth)
GET    /api/products/search?q=  → text search on name + description (MongoDB text index)
PUT    /api/products/{id}       → update own product (SELLER)
DELETE /api/products/{id}       → delete own product (SELLER)
```

Auth: gateway injects `X-User-Id` (sellerId) and `X-User-Role`. No Spring Security here.

## MongoDB Design

Collection: `products`. `@TextIndexed` on `name` (weight 3) and `description` (weight 1).
Text search uses `TextCriteria.forDefaultLanguage().matching(query)` via `MongoTemplate`.

The `skuCode` links a product to its stock entry in inventory-service. They are created separately.

## Key Files

```
entity/Product.java               — MongoDB @Document with @TextIndexed fields
service/ProductService.java       — CRUD + text search via MongoTemplate
controller/ProductController.java — REST, reads X-User-Id and X-User-Role headers
config/OpenApiConfig.java         — Springdoc bearerAuth scheme
```

## Gotchas

- MongoDB text index must exist for text search to work. On first run, Spring Data creates it via `@TextIndexed`.
- No Flyway (MongoDB is schemaless). No `ddl-auto` either.
- `sellerId` comes from gateway header — not from auth-service at request time.
