package com.pubsub.assignment.model.json;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderLineJson {
    private String lineId;
    private String itemId;
    private BigDecimal quantity;
    private String unitOfMeasure;
    private String comment;
}
