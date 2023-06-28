package com.samples.gateway.microgateway.controlller;


import com.samples.gateway.microgateway.model.Product;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fallback")
public class ProductController {

    @GetMapping("/product")
    public Product getPRoduct(){
        Product product = new Product();
        product.setId(2);
        product.setNumber("123456");
        product.setName("computer");
        return  product;
    }
}
