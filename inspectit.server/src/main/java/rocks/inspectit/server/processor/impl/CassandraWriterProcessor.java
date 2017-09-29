package rocks.inspectit.server.processor.impl;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.persistence.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalNotification;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;

import rocks.inspectit.server.cassandra.SchemaAwareCassandraDao;
import rocks.inspectit.server.processor.AbstractCmrDataProcessor;
import rocks.inspectit.shared.all.communication.DefaultData;
import rocks.inspectit.shared.all.communication.data.eum.AbstractEUMSpanDetails;
import rocks.inspectit.shared.all.communication.data.eum.AjaxRequest;
import rocks.inspectit.shared.all.communication.data.eum.EUMBeaconElement;
import rocks.inspectit.shared.all.communication.data.eum.EUMSpan;
import rocks.inspectit.shared.all.communication.data.eum.JSDomEvent;
import rocks.inspectit.shared.all.communication.data.eum.PageLoadRequest;
import rocks.inspectit.shared.all.communication.data.eum.ResourceLoadRequest;
import rocks.inspectit.shared.all.communication.data.eum.UserSessionInfo;

/**
 * @author Jonas Kunz
 *
 */
public class CassandraWriterProcessor extends AbstractCmrDataProcessor {

	private static final Logger LOG = LoggerFactory.getLogger(CassandraWriterProcessor.class);


	Cache<Long, UserSessionInfo> sessionInfoCache;

	// basically a set
	Cache<EUMSpan, EUMSpan> pendingDataPoints;

	SetMultimap<Long, EUMSpan> pendingDataPointsMap;

	@Autowired
	SchemaAwareCassandraDao cassandra;

	@PostConstruct
	private void initCache() {
		sessionInfoCache = CacheBuilder.newBuilder().softValues().expireAfterAccess(60 * 60 * 2, TimeUnit.SECONDS).build();
		pendingDataPoints = CacheBuilder.newBuilder().expireAfterAccess(2 * 60, TimeUnit.SECONDS).removalListener((RemovalNotification<EUMSpan, EUMSpan> notification) -> {
			EUMSpan span = notification.getKey();
			if (notification.getCause() != RemovalCause.EXPLICIT) {
				if (pendingDataPointsMap.remove(span.getSessionId(), span)) {
					writeSpan(Optional.empty(), span);
				}
			}
		}).build();
		pendingDataPointsMap = Multimaps.synchronizedSetMultimap(HashMultimap.<Long, EUMSpan> create());

	}

	private void writeSpan(Optional<UserSessionInfo> sessionInfo, EUMSpan span) {
		AbstractEUMSpanDetails details = span.getDetails();
		if (details instanceof AjaxRequest) {
			cassandra.insertAjax(sessionInfo, (AjaxRequest) details);
		} else if ((details instanceof JSDomEvent) && span.isRoot()) {
			cassandra.insertRootDomEvent(sessionInfo, (JSDomEvent) details);
		} else if (details instanceof PageLoadRequest) {
			cassandra.insertPageload(sessionInfo, (PageLoadRequest) details);
		} else if (details instanceof ResourceLoadRequest) {
			cassandra.insertResourceload(sessionInfo, (ResourceLoadRequest) details);
		}
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void processData(DefaultData elem, EntityManager entityManager) {
		if (elem instanceof UserSessionInfo) {
			UserSessionInfo session = (UserSessionInfo) elem;
			if (sessionInfoCache.getIfPresent(session.getSessionId()) == null) {
				sessionInfoCache.put(session.getSessionId(), session);
				Set<EUMSpan> pending = pendingDataPointsMap.removeAll(session.getSessionId());
				pendingDataPoints.invalidateAll(pending);
				for (EUMSpan pendingSpan : pending) {
					writeSpan(Optional.of(session), pendingSpan);
				}
			}
		} else if (elem instanceof EUMSpan) {
			EUMSpan span = (EUMSpan) elem;
			long sessionId = span.getSessionId();
			UserSessionInfo sessionInfo = sessionInfoCache.getIfPresent(sessionId);
			if (sessionInfo == null) {
				pendingDataPointsMap.put(sessionId, span);
				pendingDataPoints.put(span, span);
			} else {
				writeSpan(Optional.of(sessionInfo), span);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean canBeProcessed(DefaultData element) {
		return element instanceof EUMBeaconElement;
	}

}
