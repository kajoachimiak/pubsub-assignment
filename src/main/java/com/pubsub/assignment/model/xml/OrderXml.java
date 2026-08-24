package com.pubsub.assignment.model.xml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderXml {
    @JacksonXmlProperty(localName = "ID")
    private String id;

    @JacksonXmlProperty(localName = "IssueDate")
    private String issueDate;

    @JacksonXmlProperty(localName = "CustomerReference")
    private String customerReference;

    @JacksonXmlProperty(localName = "BuyerCustomerParty")
    private BuyerCustomerPartyXml buyerCustomerParty;

    @JacksonXmlProperty(localName = "OrderLine")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<OrderLineXml> orderLines;
}
