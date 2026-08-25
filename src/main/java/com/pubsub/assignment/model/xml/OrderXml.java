package com.pubsub.assignment.model.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Data;
import java.util.List;

@Data
@XmlRootElement(name = "Order")
@XmlAccessorType(XmlAccessType.FIELD)
public class OrderXml {

    @XmlElement(name = "ID")
    private String id;

    @XmlElement(name = "IssueDate")
    private String issueDate;

    @XmlElement(name = "CustomerReference")
    private String customerReference;

    @XmlElement(name = "BuyerCustomerParty")
    private BuyerCustomerPartyXml buyerCustomerParty;

    @XmlElement(name = "OrderLine")
    private List<OrderLineXml> orderLines;
}