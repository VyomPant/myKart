import org.springframework.cloud.contract.spec.Contract

Contract.make {
    label 'order_confirmed_event'
    name 'order confirmed event published to Kafka'
    description 'When order is placed and confirmed, OrderConfirmedEvent is published to order.confirmed topic'

    input {
        triggeredBy('placeOrderAndConfirm()')
    }

    outputMessage {
        sentTo 'order.confirmed'
        headers {
            header('contentType', applicationJson())
        }
        body([
            orderId      : $(regex('^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$')),
            orderNumber  : $(anyNonEmptyString()),
            buyerId      : $(anyNonEmptyString()),
            sellerId     : $(anyNonEmptyString()),
            totalAmount  : $(anyPositiveInt()),
            accountNumber: $(anyNonEmptyString()),
            ifscCode     : $(anyNonEmptyString()),
            beneficiaryName: $(anyNonEmptyString()),
            items        : $(any())
        ])
    }
}
