# product-service — Claude Code Context

**Port**: 8083 | **DB**: MongoDB (`product_db`) | **Status**: Phase 4 (AI features complete)

## Responsibility

Product catalog. Sellers create and manage products. Buyers browse, search, and optionally generate
AI-written descriptions. Does NOT handle inventory — stock is managed by inventory-service via skuCode.

## Implemented Endpoints

```
POST   /api/products                 → create product (SELLER)
GET    /api/products/{id}            → get by ID (no auth)
GET    /api/products?page&size       → paginated list (no auth)
GET    /api/products/search?q=&limit → semantic or text search (no auth)
PUT    /api/products/{id}            → update own product (SELLER)
DELETE /api/products/{id}            → delete own product (SELLER)
POST   /api/products/generate-description → AI description generation (returns 501 if disabled)
```

Auth: gateway injects `X-User-Id` (sellerId) and `X-User-Role`. No Spring Security here.

## MongoDB Design

Collection: `products`. `@TextIndexed` on `name` (weight 3) and `description` (weight 1).
The `embedding` field is `List<Double>` (1536 dimensions for `text-embedding-3-small`). It is
`null` when AI is disabled — MongoDB is schemaless so no migration is needed.

The `skuCode` links a product to its stock entry in inventory-service. Created separately.

## Spring AI Integration

**Version**: `1.0.0-M6` (latest on Maven Central; GA release was not on Central at time of writing)

**EmbeddingService** wraps `EmbeddingModel` from Spring AI. It is only active when:
1. `ai.enabled=true` (explicit flag)
2. `OPENAI_API_KEY` is set to a non-blank value

Both conditions checked in the constructor so the service won't call the API during tests or
when AI is intentionally disabled. Uses `embeddingModel.embed(String)` → `float[]`, converted
to `List<Double>` for MongoDB storage.

**SemanticSearchService** calls `embed(query)` to get a query vector, then fetches all products
and computes cosine similarity in-memory. Filters products without embeddings, sorts by score,
takes top N. Falls back to MongoDB text search if:
- AI is disabled
- Embedding throws an exception
- No products have embeddings (returns text results instead of empty)

Cosine similarity is O(n × d) where n = product count, d = 1536. Acceptable for demo scale (~1000
products). Production would use MongoDB Atlas Vector Search or a vector database.

**DescriptionGenerationService** uses `ChatClient` (auto-configured by Spring AI when the API
key is set) with a structured prompt. Returns `null` on any failure or when AI is disabled;
the controller returns HTTP 501 in that case so callers can degrade gracefully.

## Graceful Degradation

| Condition | Search behaviour | Description endpoint |
|-----------|-----------------|---------------------|
| `AI_ENABLED=false` (default) | MongoDB text search | 501 |
| `AI_ENABLED=true`, no API key | MongoDB text search | 501 |
| `AI_ENABLED=true`, valid API key | Cosine similarity + text fallback | AI-generated text |
| AI call throws at runtime | MongoDB text fallback | 501 |

## Configuration

```yaml
ai:
  enabled: ${AI_ENABLED:false}        # explicit on/off toggle
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:}     # empty = disabled
      embedding.options.model: text-embedding-3-small
      chat.options.model: gpt-4o-mini
```

## Key Files

```
entity/Product.java                         — MongoDB @Document, embedding: List<Double> field
service/EmbeddingService.java               — embed(product), isEnabled(), float[] → List<Double>
service/SemanticSearchService.java          — cosine similarity search + text fallback
service/DescriptionGenerationService.java   — ChatClient description generation
service/ProductService.java                 — CRUD; calls embedProduct() on create + update
controller/ProductController.java           — /search (semantic) + /generate-description endpoints
config/AiConfig.java                        — @ConditionalOnBean ChatClient bean definition
dto/request/GenerateDescriptionRequest.java — name, category, specs
```

## Gotchas

- `EmbeddingModel.embed(String)` returns `float[]` in Spring AI M6 — not `List<Double>`.
  Both `EmbeddingService` and `SemanticSearchService` convert explicitly via `toDoubleList()`.
- MongoDB text index must exist for text fallback to work. `@TextIndexed` on `name` and `description`
  creates it on first application start.
- `ChatClient.Builder` is auto-configured by Spring AI when `spring.ai.openai.api-key` is non-empty.
  `AiConfig` creates the `ChatClient` bean from the builder only when `ai.enabled=true` AND the
  builder bean exists.
- No Flyway — MongoDB is schemaless. Adding the `embedding` field to existing documents is a no-op.
