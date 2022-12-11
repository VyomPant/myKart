package com.mykart.productservice.controller;

import com.mykart.productservice.dto.ProductRequest;
import com.mykart.productservice.dto.ProductResponse;
import com.mykart.productservice.model.Product;
import com.mykart.productservice.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
public class ProductController {
    //constructor injection to inject ProductService into ProductController class
    private final ProductService productService;

    //endpoint to create products
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void createProduct(@RequestBody ProductRequest productRequest){
        productService.createProduct(productRequest);
    }

    //endpoint to fetch products
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<ProductResponse> getAllProducts(){
        return productService.getAllProducts();
    }

}

