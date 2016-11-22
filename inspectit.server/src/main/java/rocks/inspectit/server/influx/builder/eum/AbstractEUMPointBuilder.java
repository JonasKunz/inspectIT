package rocks.inspectit.server.influx.builder.eum;

import java.util.Collection;

import org.influxdb.dto.Point.Builder;

import rocks.inspectit.shared.all.communication.data.eum.AbstractEUMElement;
import rocks.inspectit.shared.all.communication.data.eum.PageLoadRequest;
import rocks.inspectit.shared.all.communication.data.eum.UserSessionInfo;

public abstract class AbstractEUMPointBuilder<E extends AbstractEUMElement> {

	public abstract Collection<Class<? extends AbstractEUMElement>> getSupportedTypes();

	public abstract boolean requiresSessionMetaInfo();

	public abstract boolean requiresPageLoadRequest();

	public abstract Collection<Builder> build(UserSessionInfo sessionInfo, PageLoadRequest plr, E data);

}
