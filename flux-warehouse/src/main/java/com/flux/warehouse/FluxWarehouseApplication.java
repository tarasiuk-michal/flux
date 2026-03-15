package com.flux.warehouse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class FluxWarehouseApplication {

    public static void main(String[] args) {
        SpringApplication.run(FluxWarehouseApplication.class, args);
    }
}
