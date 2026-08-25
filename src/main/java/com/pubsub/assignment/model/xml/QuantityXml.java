package com.pubsub.assignment.model.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlValue;
import lombok.Data;

@Data
@XmlAccessorType(XmlAccessType.FIELD)
public class QuantityXml {
    @XmlAttribute(name = "unitCode")
    private String unitCode;

    @XmlValue
    private Integer value;
}