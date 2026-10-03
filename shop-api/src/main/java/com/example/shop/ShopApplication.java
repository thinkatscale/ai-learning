package com.example.shop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
public class ShopApplication {
    public static void main(String[] args) throws IOException {
        // SQLite creates the file but not the folder
        String db = System.getenv().getOrDefault("DB_PATH", "./data/shop.db");
        Files.createDirectories(Path.of(db).toAbsolutePath().getParent());
        SpringApplication.run(ShopApplication.class, args);
    }
}
