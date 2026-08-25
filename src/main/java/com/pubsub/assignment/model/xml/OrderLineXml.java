package com.pubsub.assignment.model.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Data;

@Data
@XmlAccessorType(XmlAccessType.FIELD)
public class OrderLineXml {
    @XmlElement(name = "Note")
    private String note;

    @XmlElement(name = "LineItem")
    private LineItemXml lineItem;
}