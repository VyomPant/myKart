package com.mykart.productservice.service;

import com.mykart.productservice.dto.ProductRequest;
import com.mykart.productservice.dto.ProductResponse;
import com.mykart.productservice.model.Product;
import com.mykart.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {
    //constructor injection to inject ProductRepository into ProductService class
    private final ProductRepository productRepository;

    public  void createProduct(ProductRequest productRequest){
        //building instance of Product
        Product product = Product.builder()
                .name(productRequest.getName())
                .description(productRequest.getDescription())
                .price(productRequest.getPrice())
                .build();

        //saving product inside the database
        productRepository.save(product);
        log.info("Product {} is saved",product.getId());
    }

    public List<ProductResponse> getAllProducts() {
        //reading(finding) all the products inside the database and storing it in products List
        List<Product> products = productRepository.findAll();
        //mapping products List into ProductResponse class
        return products.stream().map(this::mapToProductResponse).toList();

    }

    private ProductResponse mapToProductResponse(Product product) {
        //Creating and returning object for ProductResponse using builder method
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .build();
    }
}
