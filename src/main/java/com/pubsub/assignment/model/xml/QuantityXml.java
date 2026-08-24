package com.pubsub.assignment.model.xml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class QuantityXml {
    @JacksonXmlProperty(isAttribute = true, localName = "unitCode")
    private String unitCode;

    @JacksonXmlText
    private Integer value;
}
