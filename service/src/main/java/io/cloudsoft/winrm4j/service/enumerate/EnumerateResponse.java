package io.cloudsoft.winrm4j.service.enumerate;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;

// https://docs.microsoft.com/en-us/openspecs/windows_protocols/ms-wsmv/b79bcdd9-125c-49e0-8a4f-bac4ce878592
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "EnumerateResponse", namespace = "http://schemas.xmlsoap.org/ws/2004/09/enumeration", propOrder = {
		"enumerationContext",
		"items",
		"endOfSequence"
})
public class EnumerateResponse {

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
