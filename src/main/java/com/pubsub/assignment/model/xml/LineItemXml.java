package com.pubsub.assignment.model.xml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LineItemXml {
    @JacksonXmlProperty(localName = "ID")
    private Integer id;

    @JacksonXmlProperty(localName = "Quantity")
    private QuantityXml quantity;

    @JacksonXmlProperty(localName = "Item")
    private ItemXml item;
}
