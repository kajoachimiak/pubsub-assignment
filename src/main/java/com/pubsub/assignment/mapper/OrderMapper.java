package com.pubsub.assignment.mapper;

import com.pubsub.assignment.model.json.OrderJson;
import com.pubsub.assignment.model.json.OrderLineJson;
import com.pubsub.assignment.model.xml.OrderLineXml;
import com.pubsub.assignment.model.xml.OrderXml;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(source = "id", target = "orderId")
    @Mapping(source = "buyerCustomerParty.supplierAssignedAccountID", target = "externalOrganizationId")
    @Mapping(source = "customerReference", target = "reference")
    @Mapping(source = "issueDate", target = "orderDate")
    @Mapping(source = "orderLines", target = "lines")
    OrderJson toOrderJson(OrderXml xml);

    @Mapping(source = "lineItem.id", target = "lineId")
    @Mapping(source = "lineItem.item.sellersItemIdentification.id", target = "itemId")
    @Mapping(source = "lineItem.quantity.value", target = "quantity")
    @Mapping(source = "lineItem.quantity.unitCode", target = "unitOfMeasure")
    @Mapping(source = "note", target = "comment")
    OrderLineJson toOrderLineJson(OrderLineXml xmlLine);
}