package com.pubsub.assignment.model.xml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ItemXml {
    @JacksonXmlProperty(localName = "SellersItemIdentification")
    private SellersItemIdentificationXml sellersItemIdentification;
}
