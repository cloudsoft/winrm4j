package io.cloudsoft.winrm4j.service.enumerate;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;

import java.util.ArrayList;
import java.util.List;

// https://docs.microsoft.com/en-us/openspecs/windows_protocols/ms-wsmv/dfe7084a-dea6-4f7f-b35c-cc7d1ad8060d
// https://docs.microsoft.com/en-us/openspecs/windows_protocols/ms-wsmv/0fddd40a-b5c4-4a63-a0bf-3ff9966e9e3e
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Pull", propOrder = {
		"enumerationContext",
		"maxElements",
		"any"
})
public class PullRequest {

	@XmlElement(name = "EnumerationContext", namespace = "http://schemas.xmlsoap.org/ws/2004/09/enumeration", required = true)
	protected String enumerationContext;

	@XmlElement(name = "MaxElements", namespace = "http://schemas.xmlsoap.org/ws/2004/09/enumeration")
	@XmlSchemaType(name = "unsignedLong")
	protected Long maxElements;

	@XmlAnyElement(lax = true)
	protected List<Object> any;

	public String getEnumerationContext() {
		return enumerationContext;
	}

	public void setEnumerationContext(final String enumerationContext) {
		this.enumerationContext = enumerationContext;
	}

	public Long getMaxElements() {
		return maxElements;
	}

	public void setMaxElements(final Long maxElements) {
		this.maxElements = maxElements;
	}

	public List<Object> getAny() {
		if (any == null) {
			any = new ArrayList<>();
		}
		return any;
	}

	public void setAny(final List<Object> any) {
		this.any = any;
	}

}
