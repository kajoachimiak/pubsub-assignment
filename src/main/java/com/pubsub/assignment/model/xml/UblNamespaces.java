package com.pubsub.assignment.model.xml;

/**
 * UBL 2.1 XML namespace URIs used to bind {@code Order-2} documents via namespace-aware JAXB.
 */
public final class UblNamespaces {

    /** {@code xmlns} default namespace - the root {@code Order} document element. */
    public static final String ORDER = "urn:oasis:names:specification:ubl:schema:xsd:Order-2";

    /** {@code xmlns:cbc} - Common Basic Components (simple/leaf elements). */
    public static final String CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";

    /** {@code xmlns:cac} - Common Aggregate Components (complex/container elements). */
    public static final String CAC = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";

    private UblNamespaces() {
    }
}
