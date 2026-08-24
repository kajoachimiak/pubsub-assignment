package com.pubsub.assignment.model.xml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderLineXml {
    @JacksonXmlProperty(localName = "Note")
    private String note;

    @JacksonXmlProperty(localName = "LineItem")
    private LineItemXml lineItem;
}
