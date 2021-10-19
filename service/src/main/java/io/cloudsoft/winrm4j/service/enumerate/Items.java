package io.cloudsoft.winrm4j.service.enumerate;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAnyElement;

import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
public class Items {

	@XmlAnyElement(lax = true)
	protected List<Object> value;

	public List<Object> getValue() {
		return value;
	}

	public void setValue(final List<Object> value) {
		this.value = value;
	}
}
