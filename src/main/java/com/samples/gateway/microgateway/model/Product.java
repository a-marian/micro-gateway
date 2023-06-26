package com.samples.gateway.microgateway.model;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class Product {

    private Integer id;
    private String number;
    private String name;
}
