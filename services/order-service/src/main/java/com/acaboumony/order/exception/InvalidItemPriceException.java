package com.acaboumony.order.exception;

/**
 * Thrown when an order item has an invalid unit price (zero or negative).
 */
public class InvalidItemPriceException extends OrderServiceException {

    public InvalidItemPriceException() {
        super("INVALID_ITEM_PRICE", "Item unit price must be greater than zero");
    }
}
