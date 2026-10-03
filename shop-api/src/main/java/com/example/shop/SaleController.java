package com.example.shop;

import com.example.shop.Models.NewSale;
import com.example.shop.Models.Sale;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/sales")
public class SaleController {

    private final SaleService service;

    public SaleController(SaleService service) {
        this.service = service;
    }

    @GetMapping
    public List<Sale> list(@RequestParam(required = false) Long productId,
                           @RequestParam(required = false) Long customerId,
                           @RequestParam(required = false) String region,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           @RequestParam(required = false) Long afterId,
                           @RequestParam(defaultValue = "50") int size) {
        return service.list(productId, customerId, region, from, to, afterId, size);
    }

    @GetMapping("/{id}")
    public Sale get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Sale create(@Valid @RequestBody NewSale body) {
        return service.create(body);
    }
}
