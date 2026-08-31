package com.pubsub.assignment.mapper;

import com.pubsub.assignment.model.json.OrderJson;
import com.pubsub.assignment.model.xml.*;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderMapperTest {

    private final OrderMapper orderMapper = Mappers.getMapper(OrderMapper.class);

    @Test
    void shouldMapOrderXmlToOrderJsonCorrectly() {
        // given
        OrderXml xml = new OrderXml();
        xml.setId("12345");
        xml.setIssueDate("2026-08-24");
        xml.setCustomerReference("REF-001");

        BuyerCustomerPartyXml buyer = new BuyerCustomerPartyXml();
        buyer.setSupplierAssignedAccountID("ORG-999");
        xml.setBuyerCustomerParty(buyer);

        QuantityXml quantity = new QuantityXml();
        quantity.setValue(new BigDecimal("100.5"));
        quantity.setUnitCode("KGM");

        SellersItemIdentificationXml sellersItem = new SellersItemIdentificationXml();
        sellersItem.setId("ITEM-XYZ");

        ItemXml item = new ItemXml();
        item.setSellersItemIdentification(sellersItem);

        LineItemXml lineItem = new LineItemXml();
        lineItem.setId("LINE-1A");
        lineItem.setQuantity(quantity);
        lineItem.setItem(item);

        OrderLineXml orderLine = new OrderLineXml();
        orderLine.setNote("Fragile package");
        orderLine.setLineItem(lineItem);

        xml.setOrderLines(List.of(orderLine));

        // when
        OrderJson json = orderMapper.toOrderJson(xml);

        // then
        assertThat(json).isNotNull();
        assertThat(json.getOrderId()).isEqualTo("12345");
        assertThat(json.getOrderDate()).isEqualTo("2026-08-24");
        assertThat(json.getReference()).isEqualTo("REF-001");
        assertThat(json.getExternalOrganizationId()).isEqualTo("ORG-999");

        assertThat(json.getLines()).hasSize(1);
        assertThat(json.getLines().get(0).getLineId()).isEqualTo("LINE-1A");
        assertThat(json.getLines().get(0).getQuantity()).isEqualByComparingTo(new BigDecimal("100.5"));
        assertThat(json.getLines().get(0).getUnitOfMeasure()).isEqualTo("KGM");
        assertThat(json.getLines().get(0).getItemId()).isEqualTo("ITEM-XYZ");
        assertThat(json.getLines().get(0).getComment()).isEqualTo("Fragile package");
    }

    @Test
    void shouldReturnNullWhenOrderXmlIsNull() {
        // when
        OrderJson json = orderMapper.toOrderJson(null);

        // then
        assertThat(json).isNull();
    }
}