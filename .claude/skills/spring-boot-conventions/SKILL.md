# Skill: Spring Boot Conventions

Auto-invoked when creating controllers, services, or exception handlers in any myKart service.

## GlobalExceptionHandler

Every service must have a `@RestControllerAdvice` that maps all exceptions to `ApiError`
(from `mkart-common`). Never let a raw exception reach the HTTP response.

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        var fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return ApiError.of(400, "Validation Failed", "Request validation failed", req.getRequestURI(), fieldErrors);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
        return ApiError.of(404, "Not Found", ex.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception at {}", req.getRequestURI(), ex);
        return ApiError.of(500, "Internal Server Error", "An unexpected error occurred", req.getRequestURI());
    }
}
```

## Request validation

Always annotate controller arguments:

```java
@PostMapping("/orders")
public OrderResponse placeOrder(@Valid @RequestBody PlaceOrderRequest request,
                                @RequestHeader("X-User-Id") String userId) {
```

Use Jakarta Validation annotations on request records:

```java
public record PlaceOrderRequest(
        @NotNull @Size(min = 1) List<@Valid LineItemRequest> items
) {}
```

## Transaction discipline

```java
// Service methods that READ data:
@Transactional(readOnly = true)
public OrderResponse findById(UUID id) { ... }

// Service methods that WRITE data:
@Transactional
public OrderResponse placeOrder(...) { ... }
```

Never put `@Transactional` on controllers — only on service methods.

## Structured logging with MDC

Add correlation IDs to every log line for distributed tracing:

```java
@KafkaListener(topics = "order.confirmed")
public void onOrderConfirmed(String payload) {
    var event = objectMapper.readValue(payload, OrderConfirmedEvent.class);
    MDC.put("orderId", event.orderId());
    MDC.put("sellerId", event.sellerId());
    try {
        // process event
        log.info("Order confirmed: orderNumber={}", event.orderNumber());
    } finally {
        MDC.clear();
    }
}
```

## OpenAPI annotations

All controller methods must have `@Operation`:

```java
@Operation(
    summary = "Reserve stock for an order",
    description = "Atomically reserves quantity for all line items. Fails entirely if any SKU has insufficient stock.",
    responses = {
        @ApiResponse(responseCode = "200", description = "All items reserved"),
        @ApiResponse(responseCode = "409", description = "Insufficient stock for one or more items")
    }
)
@PostMapping("/reserve")
public ReservationResponse reserve(@Valid @RequestBody ReservationRequest request) { ... }
```

## No System.out.println

Always use SLF4J:

```java
private static final Logger log = LoggerFactory.getLogger(MyClass.class);
// or in Kotlin:
private val log = LoggerFactory.getLogger(MyClass::class.java)
```

## Bean naming

No abbreviations. `inventorySvc` → `inventoryService`. `prodRepo` → `productRepository`.
Method names should read as sentences: `placeOrder`, `reserveStock`, `getCurrentUser`.
