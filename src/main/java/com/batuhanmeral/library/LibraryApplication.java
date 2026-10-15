package com.batuhanmeral.library;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * {@code @ConfigurationPropertiesScan}, bu paketin altındaki her {@code @ConfigurationProperties}
 * sınıfını bean yapar.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class LibraryApplication {

    public static void main(String[] args) {
        SpringApplication.run(LibraryApplication.class, args);
    }
}
