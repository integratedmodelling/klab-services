package org.integratedmodelling.klab.api.lang.kactors.beans;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Used bidirectionally: from the view to communicate user actions on components
 * to an application, and from the application to send changes and settings to
 * components previously created.
 * 
 * @author Ferd
 *
 */
public class ViewAction {

	/**
	 * When it comes from the view, should include the component
	 */
	private ViewComponent component = null;

	/**
	 * When coming from the controller, must include the component ID (the #tag in
	 * the k.Actors source, equivalent to {@link ViewComponent#getName()}.and an
	 * operation
	 */
	private String componentTag;
	private String applicationId;
	private Boolean booleanValue = null;
	private Double doubleValue = null;
	private Integer intValue = null;
	private String stringValue = null;
	private Date dateValue = null;
	private List<String> listValue = null;
	private Map<String, String> data = null;

	public ViewAction() {
	}

	public ViewAction(ViewComponent component) {
		this.component = component;
	}

	public ViewAction(ViewComponent component, String stringValue) {
		this.component = component;
		this.stringValue = stringValue;
	}

	public ViewAction(ViewComponent component, boolean value) {
		this.component = component;
		this.booleanValue = value;
	}

	public Boolean isBooleanValue() {
		return booleanValue;
	}

	public void setBooleanValue(boolean booleanValue) {
		this.booleanValue = booleanValue;
	}

	public Double getDoubleValue() {
		return doubleValue;
	}

	public void setDoubleValue(double doubleValue) {
		this.doubleValue = doubleValue;
	}

	public Integer getIntValue() {
		return intValue;
	}

	public void setIntValue(int intValue) {
		this.intValue = intValue;
	}

	public String getStringValue() {
		return stringValue;
	}

	public void setStringValue(String stringValue) {
		this.stringValue = stringValue;
	}

	public Map<String, String> getData() {
		return data;
	}

	public void setData(Map<String, String> data) {
		this.data = data;
	}

	public ViewComponent getComponent() {
		return component;
	}

	public void setComponent(ViewComponent component) {
		this.component = component;
	}

	public Date getDateValue() {
		return dateValue;
	}

	public void setDateValue(Date dateValue) {
		this.dateValue = dateValue;
	}

	public String getComponentTag() {
		return componentTag;
	}

	public void setComponentTag(String componentTag) {
		this.componentTag = componentTag;
	}

	public String getApplicationId() {
		return applicationId;
	}

	public void setApplicationId(String applicationId) {
		this.applicationId = applicationId;
	}

	@Override
	public String toString() {
		return "ViewAction [componentTag=" + componentTag + ", booleanValue=" + booleanValue + ", doubleValue="
				+ doubleValue + ", intValue=" + intValue + ", stringValue=" + stringValue + ", dateValue=" + dateValue
				+ ", data=" + data + "]";
	}

	public List<String> getListValue() {
		return listValue;
	}

	public void setListValue(List<String> listValue) {
		this.listValue = listValue;
	}
	

}
