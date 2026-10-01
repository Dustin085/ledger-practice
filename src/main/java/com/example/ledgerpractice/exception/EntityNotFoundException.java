package com.example.ledgerpractice.exception;

public class EntityNotFoundException extends RuntimeException {
    // 泛用建構子：自訂完整訊息
    public EntityNotFoundException(String message) {
        super(message);
    }

    // 便捷建構子：帶入 Entity 類別與 ID，自動組合標準訊息
    public EntityNotFoundException(Class<?> entityClass, Object id) {
        super(String.format("%s not found with id: %s", entityClass.getSimpleName(), id));
    }
}
