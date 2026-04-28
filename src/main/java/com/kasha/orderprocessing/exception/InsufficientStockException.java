package com.kasha.orderprocessing.exception;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String productName, int available) {
        super("Insufficient stock for '" + productName + "'. Available: " + available);
    }
}
