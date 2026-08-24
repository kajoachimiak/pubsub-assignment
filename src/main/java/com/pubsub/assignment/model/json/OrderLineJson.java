package com.pubsub.assignment.model.json;

import lombok.Data;

@Data
public class OrderLineJson {
    private Integer lineId;
    private String itemId;
    private Integer quantity;
    private String unitOfMeasure;
    private String comment;
}
