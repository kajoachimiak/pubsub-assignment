package com.pubsub.assignment.model.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Data;
import java.util.List;

@Data
@XmlRootElement(name = "Order", namespace = UblNamespaces.ORDER)
@XmlAccessorType(XmlAccessType.FIELD)
public class OrderXml {

    @XmlElement(name = "ID", namespace = UblNamespaces.CBC)
    private String id;

    @XmlElement(name = "IssueDate", namespace = UblNamespaces.CBC)
    private String issueDate;

    @XmlElement(name = "CustomerReference", namespace = UblNamespaces.CBC)
    private String customerReference;

    @XmlElement(name = "BuyerCustomerParty", namespace = UblNamespaces.CAC)
    private BuyerCustomerPartyXml buyerCustomerParty;

    @XmlElement(name = "OrderLine", namespace = UblNamespaces.CAC)
    private List<OrderLineXml> orderLines;
}