package com.pubsub.assignment.model.json;

import lombok.Data;

import java.util.List;

@Data
public class OrderJson {
    private String orderId;
    private String externalOrganizationId;
    private String reference;
    private String orderDate;
    private List<OrderLineJson> lines;
}
