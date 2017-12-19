package rocks.inspectit.shared.cs.ci.eum;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import rocks.inspectit.shared.all.instrumentation.config.impl.AgentEumDomEventSelector;

/**
 * Config corresponding to {@link AgentEumDomEventSelector}.
 *
 * @author Jonas Kunz
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "eum-dom-event-selector")
public class EumDomEventSelector {

	/**
	 * See {@link AgentEumDomEventSelector#eventsList}.
	 */
	@XmlAttribute(name = "eventsList", required = true)
	private String eventsList;

	/**
	 * See {@link AgentEumDomEventSelector#selector}.
	 */
	@XmlAttribute(name = "selector", required = true)
	private String selector;

	/**
	 * See {@link AgentEumDomEventSelector#attributesToExtractList}.
	 */
	@XmlAttribute(name = "attributesToExtractList", required = true)
	private String attributesToExtractList;

	/**
	 * See {@link AgentEumDomEventSelector#alwaysRelevant}.
	 */
	@XmlAttribute(name = "alwaysRelevant", required = true)
	private boolean alwaysRelevant;

	/**
	 * See {@link AgentEumDomEventSelector#considerBubbling}.
	 */
	@XmlAttribute(name = "considerBubbling", required = true)
	private boolean considerBubbling;

	/**
	 * See {@link AgentEumDomEventSelector#storagePrefix}.
	 */
	@XmlAttribute(name = "storagePrefix", required = true)
	private String storagePrefix;

	/**
	 * Gets {@link #eventsList}.
	 *
	 * @return {@link #eventsList}
	 */
	public String getEventsList() {
		return this.eventsList;
	}

	/**
	 * Sets {@link #eventsList}.
	 *
	 * @param eventsList
	 *            New value for {@link #eventsList}
	 */
	public void setEventsList(String eventsList) {
		this.eventsList = eventsList;
	}

	/**
	 * Gets {@link #selector}.
	 *
	 * @return {@link #selector}
	 */
	public String getSelector() {
		return this.selector;
	}

	/**
	 * Sets {@link #selector}.
	 *
	 * @param selector
	 *            New value for {@link #selector}
	 */
	public void setSelector(String selector) {
		this.selector = selector;
	}

	/**
	 * Gets {@link #attributesToExtractList}.
	 *
	 * @return {@link #attributesToExtractList}
	 */
	public String getAttributesToExtractList() {
		return this.attributesToExtractList;
	}

	/**
	 * Sets {@link #attributesToExtractList}.
	 *
	 * @param attributesToExtractList
	 *            New value for {@link #attributesToExtractList}
	 */
	public void setAttributesToExtractList(String attributesToExtractList) {
		this.attributesToExtractList = attributesToExtractList;
	}

	/**
	 * Gets {@link #alwaysRelevant}.
	 *
	 * @return {@link #alwaysRelevant}
	 */
	public boolean isAlwaysRelevant() {
		return this.alwaysRelevant;
	}

	/**
	 * Sets {@link #alwaysRelevant}.
	 *
	 * @param alwaysRelevant
	 *            New value for {@link #alwaysRelevant}
	 */
	public void setAlwaysRelevant(boolean alwaysRelevant) {
		this.alwaysRelevant = alwaysRelevant;
	}

	/**
	 * Gets {@link #considerBubbling}.
	 *
	 * @return {@link #considerBubbling}
	 */
	public boolean isConsiderBubbling() {
		return this.considerBubbling;
	}

	/**
	 * Sets {@link #considerBubbling}.
	 *
	 * @param considerBubbling
	 *            New value for {@link #considerBubbling}
	 */
	public void setConsiderBubbling(boolean considerBubbling) {
		this.considerBubbling = considerBubbling;
	}

	/**
	 * Gets {@link #storagePrefix}.
	 *
	 * @return {@link #storagePrefix}
	 */
	public String getStoragePrefix() {
		return this.storagePrefix;
	}

	/**
	 * Sets {@link #storagePrefix}.
	 *
	 * @param storagePrefix
	 *            New value for {@link #storagePrefix}
	 */
	public void setStoragePrefix(String storagePrefix) {
		this.storagePrefix = storagePrefix;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = (prime * result) + (this.alwaysRelevant ? 1231 : 1237);
		result = (prime * result) + ((this.attributesToExtractList == null) ? 0 : this.attributesToExtractList.hashCode());
		result = (prime * result) + (this.considerBubbling ? 1231 : 1237);
		result = (prime * result) + ((this.eventsList == null) ? 0 : this.eventsList.hashCode());
		result = (prime * result) + ((this.selector == null) ? 0 : this.selector.hashCode());
		result = (prime * result) + ((this.storagePrefix == null) ? 0 : this.storagePrefix.hashCode());
		return result;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		EumDomEventSelector other = (EumDomEventSelector) obj;
		if (this.alwaysRelevant != other.alwaysRelevant) {
			return false;
		}
		if (this.attributesToExtractList == null) {
			if (other.attributesToExtractList != null) {
				return false;
			}
		} else if (!this.attributesToExtractList.equals(other.attributesToExtractList)) {
			return false;
		}
		if (this.considerBubbling != other.considerBubbling) {
			return false;
		}
		if (this.eventsList == null) {
			if (other.eventsList != null) {
				return false;
			}
		} else if (!this.eventsList.equals(other.eventsList)) {
			return false;
		}
		if (this.selector == null) {
			if (other.selector != null) {
				return false;
			}
		} else if (!this.selector.equals(other.selector)) {
			return false;
		}
		if (this.storagePrefix == null) {
			if (other.storagePrefix != null) {
				return false;
			}
		} else if (!this.storagePrefix.equals(other.storagePrefix)) {
			return false;
		}
		return true;
	}


}
