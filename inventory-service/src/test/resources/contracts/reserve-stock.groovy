import org.springframework.cloud.contract.spec.Contract

Contract.make {
    label 'inventory_reserve_success'
    name 'reserve items successfully'
    description 'POST /api/inventory/reserve reserves stock and returns available quantity'

    request {
        method POST()
        url '/api/inventory/reserve'
        headers { contentType applicationJson() }
        body([
            items: [
                [skuCode: 'SKU-CONTRACT-001', quantity: 2]
            ]
        ])
    }

    response {
        status 200
        headers { contentType applicationJson() }
        body([
            reserved: true,
            items   : [
                [skuCode: 'SKU-CONTRACT-001', reservedQuantity: 2]
            ]
        ])
    }
}
