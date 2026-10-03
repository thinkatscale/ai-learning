package com.example.shop;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class Models {
    private Models() {}

    public record Product(long id, String sku, String name, String category, double unitPrice) {}

    public record InventoryItem(long productId, String sku, String name,
                                int quantityOnHand, int reorderLevel, String updatedAt) {}

    public record Sale(long id, long productId, long customerId, String region,
                       int quantity, double unitPrice, double total, String saleDate) {}

    public record NewSale(@NotNull Long productId,
                          @NotNull Long customerId,
                          @NotBlank String region,
                          @Min(1) int quantity) {}

    public record StockUpdate(@Min(0) int quantityOnHand) {}

    public record AgentRequest(@NotBlank @Size(max = 1000) String question) {}

    public record ToolCallTrace(String tool, String arguments, String resultPreview) {}

    public record AgentResponse(String answer, List<ToolCallTrace> toolCalls, int steps, int totalTokens) {}
}
