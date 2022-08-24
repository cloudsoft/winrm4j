package io.cloudsoft.winrm4j.service.enumerate;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

// https://docs.microsoft.com/en-us/openspecs/windows_protocols/ms-wsmv/939e283a-5518-4e43-9d9f-4f0b1a199815
// https://docs.microsoft.com/en-us/openspecs/windows_protocols/ms-wsmv/8923a1bb-ea8b-49cb-8495-5f2612e7a0f9
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PullResponse", namespace = "http://schemas.xmlsoap.org/ws/2004/09/enumeration", propOrder = {
		"enumerationContext",
		"items",
		"endOfSequence"
})
public class PullResponse {

	@XmlElement(name = "EnumerationContext", namespace = "http://schemas.xmlsoap.org/ws/2004/09/enumeration")
	protected String enumerationContext;

	@XmlElement(name = "Items", namespace = "http://schemas.xmlsoap.org/ws/2004/09/enumeration")
	protected Items items;

	@XmlElement(name = "EndOfSequence", namespace = "http://schemas.xmlsoap.org/ws/2004/09/enumeration")
	protected String endOfSequence;

	public String getEnumerationContext() {
		return enumerationContext;
	}

	public void setEnumerationContext(final String enumerationContext) {
		this.enumerationContext = enumerationContext;
	}

	public Items getItems() {
		return items;
	}

	public void setItems(final Items items) {
		this.items = items;
	}

	public String getEndOfSequence() {
		return endOfSequence;
	}

	public void setEndOfSequence(final String endOfSequence) {
		this.endOfSequence = endOfSequence;
	}
}
