package com.pubsub.assignment.model.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Data;

@Data
@XmlAccessorType(XmlAccessType.FIELD)
public class LineItemXml {
    @XmlElement(name = "ID", namespace = UblNamespaces.CBC)
    private String id;

    @XmlElement(name = "Quantity", namespace = UblNamespaces.CBC)
    private QuantityXml quantity;

    @XmlElement(name = "Item", namespace = UblNamespaces.CAC)
    private ItemXml item;
}