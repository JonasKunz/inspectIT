package rocks.inspectit.server.cassandra;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.DataType;
import com.datastax.driver.core.LocalDate;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.schemabuilder.SchemaBuilder;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import rocks.inspectit.shared.all.communication.data.eum.AjaxRequest;
import rocks.inspectit.shared.all.communication.data.eum.EUMSpan;
import rocks.inspectit.shared.all.communication.data.eum.JSDomEvent;
import rocks.inspectit.shared.all.communication.data.eum.PageLoadRequest;
import rocks.inspectit.shared.all.communication.data.eum.PageLoadRequest.NavigationTimings;
import rocks.inspectit.shared.all.communication.data.eum.ResourceLoadRequest;
import rocks.inspectit.shared.all.communication.data.eum.UserSessionInfo;

/**
 * @author Jonas Kunz
 *
 */
@Component
public class SchemaAwareCassandraDao {

	private static final Logger LOG = LoggerFactory.getLogger(SchemaAwareCassandraDao.class);

	@Autowired
	CassandraDao cassandra;

	private PreparedStatement insertResourceLoad;
	private PreparedStatement insertAjax;
	private PreparedStatement insertPageLoad;
	private PreparedStatement insertRootDomEvent;

	private AtomicBoolean isConnectedAndSchemaSetUp = new AtomicBoolean(false);

	private CassandraConnectionStateListener connectionListener = new CassandraConnectionStateListener() {
		@Override
		public void disconnected(CassandraDao cassandra) {
			isConnectedAndSchemaSetUp.set(false);
		}

		@Override
		public void connected(CassandraDao cassandra) {
			initSchema();
		}
	};

	@PostConstruct
	private void init() {
		synchronized (cassandra) {
			cassandra.addConnectionStateListener(connectionListener);
			if (isConnected()) {
				initSchema();
			}
		}
	}

	public boolean isConnected() {
		return cassandra.isConnected();
	}

	public void insertAjax(Optional<UserSessionInfo> sessionInfo, AjaxRequest ajax) {
		if (isConnectedAndSchemaSetUp.get()) {
			EUMSpan span = ajax.getOwningSpan();
			BoundStatement insert = insertAjax.bind()
					.setDate(CassandraSchema.AjaxRequests.DAY, LocalDate.fromMillisSinceEpoch(span.getTimeStamp().getTime()))
					.setTimestamp(CassandraSchema.AjaxRequests.TIME, span.getTimeStamp())
					.setLong(CassandraSchema.AjaxRequests.TRACE_ID, span.getSpanIdent().getTraceId())
					.setLong(CassandraSchema.AjaxRequests.SPAN_ID, span.getSpanIdent().getId())
					.setLong(CassandraSchema.AjaxRequests.SESSION_ID, span.getSessionId())
					.setLong(CassandraSchema.AjaxRequests.TAB_ID, span.getTabId())
					.setDouble(CassandraSchema.AjaxRequests.DURATION, span.getDuration())
					.setString(CassandraSchema.AjaxRequests.URL, ajax.getUrl())
					.setString(CassandraSchema.AjaxRequests.BASE_URL, ajax.getBaseUrl())
					.setInt(CassandraSchema.AjaxRequests.STATUS, ajax.getStatus());
			sessionInfo.ifPresent((s) -> addSessionInfoToInsert(s, insert));
			listenForErrors(cassandra.execute(insert));
		}
	}

	public void insertResourceload(Optional<UserSessionInfo> sessionInfo, ResourceLoadRequest resource) {
		if (isConnectedAndSchemaSetUp.get()) {
			EUMSpan span = resource.getOwningSpan();
			BoundStatement insert = insertResourceLoad.bind()
					.setDate(CassandraSchema.ResourceLoadRequests.DAY, LocalDate.fromMillisSinceEpoch(span.getTimeStamp().getTime()))
					.setTimestamp(CassandraSchema.ResourceLoadRequests.TIME, span.getTimeStamp())
					.setLong(CassandraSchema.ResourceLoadRequests.TRACE_ID, span.getSpanIdent().getTraceId())
					.setLong(CassandraSchema.ResourceLoadRequests.SPAN_ID, span.getSpanIdent().getId())
					.setLong(CassandraSchema.ResourceLoadRequests.SESSION_ID, span.getSessionId())
					.setLong(CassandraSchema.ResourceLoadRequests.TAB_ID, span.getTabId())
					.setDouble(CassandraSchema.ResourceLoadRequests.DURATION, span.getDuration())
					.setString(CassandraSchema.ResourceLoadRequests.URL, resource.getUrl()).setString(CassandraSchema.ResourceLoadRequests.BASE_URL, resource.getBaseUrl())
					.setString(CassandraSchema.ResourceLoadRequests.INITIATOR_TYPE, resource.getInitiatorType())
					.setLong(CassandraSchema.ResourceLoadRequests.TRANSFER_SIZE, resource.getTransferSize());
			sessionInfo.ifPresent((s) -> addSessionInfoToInsert(s, insert));
			listenForErrors(cassandra.execute(insert));
		}
	}



	public void insertRootDomEvent(Optional<UserSessionInfo> sessionInfo, JSDomEvent action) {
		if (isConnectedAndSchemaSetUp.get()) {
			EUMSpan span = action.getOwningSpan();
			BoundStatement insert = insertRootDomEvent.bind()
					.setDate(CassandraSchema.RootDomEvents.DAY, LocalDate.fromMillisSinceEpoch(span.getTimeStamp().getTime()))
					.setTimestamp(CassandraSchema.RootDomEvents.TIME, span.getTimeStamp())
					.setLong(CassandraSchema.RootDomEvents.TRACE_ID, span.getSpanIdent().getTraceId())
					.setLong(CassandraSchema.RootDomEvents.SPAN_ID, span.getSpanIdent().getId())
					.setLong(CassandraSchema.RootDomEvents.SESSION_ID, span.getSessionId())
					.setLong(CassandraSchema.RootDomEvents.TAB_ID, span.getTabId())
					.setBool(CassandraSchema.RootDomEvents.RELEVANT_THROUGH_SELECTOR, action.isRelevantThroughSelector())
					.setString(CassandraSchema.RootDomEvents.BASE_URL, action.getBaseUrl())
					.setString(CassandraSchema.RootDomEvents.ACTION_TYPE, action.getEventType())
					.setMap(CassandraSchema.RootDomEvents.ELEMENT_INFO, action.getElementInfo());
			sessionInfo.ifPresent((s) -> addSessionInfoToInsert(s, insert));
			listenForErrors(cassandra.execute(insert));
		}
	}

	@SuppressWarnings("PMD.NullAssignment")
	public void insertPageload(Optional<UserSessionInfo> sessionInfo, PageLoadRequest load) {
		if (isConnectedAndSchemaSetUp.get()) {
			EUMSpan span = load.getOwningSpan();
			final NavigationTimings nt = load.getNavigationTimings();
			Function<Function<NavigationTimings, Double>, Timestamp> asTimestamp = (getter) -> (getter.apply(nt) != 0d ? new Timestamp(Math.round(getter.apply(nt))) : null);
			BoundStatement insert = insertPageLoad.bind()
					.setDate(CassandraSchema.PageLoadRequests.DAY, LocalDate.fromMillisSinceEpoch(span.getTimeStamp().getTime()))
					.setTimestamp(CassandraSchema.PageLoadRequests.TIME, span.getTimeStamp())
					.setLong(CassandraSchema.PageLoadRequests.TRACE_ID, span.getSpanIdent().getTraceId())
					.setLong(CassandraSchema.PageLoadRequests.SPAN_ID, span.getSpanIdent().getId())
					.setLong(CassandraSchema.PageLoadRequests.SESSION_ID, span.getSessionId())
					.setLong(CassandraSchema.PageLoadRequests.TAB_ID, span.getTabId())
					.setDouble(CassandraSchema.PageLoadRequests.DURATION, span.getDuration())
					.setString(CassandraSchema.PageLoadRequests.URL, load.getUrl())
					.setInt(CassandraSchema.PageLoadRequests.RESOURCE_COUNT, load.getResourceCount());
			if (nt != null) {
				insert.setTimestamp(CassandraSchema.PageLoadRequests.FIRST_PAINT, asTimestamp.apply(NavigationTimings::getFirstPaint))
				.setTimestamp(CassandraSchema.PageLoadRequests.UNLOAD_EVENT_START, asTimestamp.apply(NavigationTimings::getUnloadEventStart))
				.setTimestamp(CassandraSchema.PageLoadRequests.UNLOAD_EVENT_END, asTimestamp.apply(NavigationTimings::getUnloadEventEnd))
				.setTimestamp(CassandraSchema.PageLoadRequests.REDIRECT_START, asTimestamp.apply(NavigationTimings::getRedirectStart))
				.setTimestamp(CassandraSchema.PageLoadRequests.REDIRECT_END, asTimestamp.apply(NavigationTimings::getRedirectEnd))
				.setTimestamp(CassandraSchema.PageLoadRequests.FETCH_START, asTimestamp.apply(NavigationTimings::getFetchStart))
				.setTimestamp(CassandraSchema.PageLoadRequests.DOMAIN_LOOKUP_START, asTimestamp.apply(NavigationTimings::getDomainLookupStart))
				.setTimestamp(CassandraSchema.PageLoadRequests.CONNECT_START, asTimestamp.apply(NavigationTimings::getConnectStart))
				.setTimestamp(CassandraSchema.PageLoadRequests.CONNECT_END, asTimestamp.apply(NavigationTimings::getConnectEnd))
				.setTimestamp(CassandraSchema.PageLoadRequests.SECURE_CONNECTION_START, asTimestamp.apply(NavigationTimings::getSecureConnectionStart))
				.setTimestamp(CassandraSchema.PageLoadRequests.REQUEST_START, asTimestamp.apply(NavigationTimings::getRequestStart))
				.setTimestamp(CassandraSchema.PageLoadRequests.RESPONSE_START, asTimestamp.apply(NavigationTimings::getResponseStart))
				.setTimestamp(CassandraSchema.PageLoadRequests.RESPONSE_END, asTimestamp.apply(NavigationTimings::getResponseEnd))
				.setTimestamp(CassandraSchema.PageLoadRequests.DOM_LOADING, asTimestamp.apply(NavigationTimings::getDomLoading))
				.setTimestamp(CassandraSchema.PageLoadRequests.DOM_INTERACTIVE, asTimestamp.apply(NavigationTimings::getDomInteractive))
				.setTimestamp(CassandraSchema.PageLoadRequests.DOM_CONTENT_LOADED_EVENT_START, asTimestamp.apply(NavigationTimings::getDomContentLoadedEventStart))
				.setTimestamp(CassandraSchema.PageLoadRequests.DOM_CONTENT_LOADED_EVENT_END, asTimestamp.apply(NavigationTimings::getDomContentLoadedEventEnd))
				.setTimestamp(CassandraSchema.PageLoadRequests.DOM_COMPLETE, asTimestamp.apply(NavigationTimings::getDomComplete))
				.setTimestamp(CassandraSchema.PageLoadRequests.LOAD_EVENT_START, asTimestamp.apply(NavigationTimings::getLoadEventStart));
				if (nt.getSpeedIndex() != 0d) {
					insert.setDouble(CassandraSchema.PageLoadRequests.SPEEDINDEX, nt.getSpeedIndex());
				}
			}
			sessionInfo.ifPresent((s) -> addSessionInfoToInsert(s, insert));
			listenForErrors(cassandra.execute(insert));
		}
	}

	private void addSessionInfoToInsert(UserSessionInfo sessionInfo, BoundStatement insert) {
		insert
		.setString(CassandraSchema.EumTable.BROWSER, sessionInfo.getBrowser())
		.setString(CassandraSchema.EumTable.DEVICE, sessionInfo.getDevice())
		.setString(CassandraSchema.EumTable.LANGUAGE, sessionInfo.getLanguage());
	}


	private void listenForErrors(ListenableFuture<ResultSet> future) {
		future.addListener(() -> {
			try {
				future.get();
			} catch (ExecutionException e) {
				LOG.error("Error executing query!", e.getCause());
			} catch (InterruptedException e) {
				LOG.error("Query interrupted!", e.getCause());
			}
		}, MoreExecutors.directExecutor());
	}

	private void initSchema() {
		try {
			initEUMTables().get();
			prepareEUMStatements().get();
			isConnectedAndSchemaSetUp.set(true);
		} catch (Exception e) {
			LOG.error("Error setting up schema", e);
			isConnectedAndSchemaSetUp.set(false);
		}
	}

	private Future<?> initEUMTables() {
		ListenableFuture<ResultSet> ajaxFut = cassandra.execute(
				SchemaBuilder.createTable(CassandraSchema.AjaxRequests.TABLE_NAME)
				.ifNotExists()
				.addPartitionKey(CassandraSchema.AjaxRequests.DAY, DataType.date())
				.addClusteringColumn(CassandraSchema.AjaxRequests.TIME, DataType.timestamp())
				.addClusteringColumn(CassandraSchema.AjaxRequests.SPAN_ID, DataType.bigint())
				.addColumn(CassandraSchema.AjaxRequests.TRACE_ID, DataType.bigint())
				.addColumn(CassandraSchema.AjaxRequests.SESSION_ID, DataType.bigint())
				.addColumn(CassandraSchema.AjaxRequests.TAB_ID, DataType.bigint())
				.addColumn(CassandraSchema.AjaxRequests.BROWSER, DataType.text())
				.addColumn(CassandraSchema.AjaxRequests.DEVICE, DataType.text())
				.addColumn(CassandraSchema.AjaxRequests.LANGUAGE, DataType.text())
				.addColumn(CassandraSchema.AjaxRequests.DURATION, DataType.cdouble())
				.addColumn(CassandraSchema.AjaxRequests.URL, DataType.text())
				.addColumn(CassandraSchema.AjaxRequests.BASE_URL, DataType.text())
				.addColumn(CassandraSchema.AjaxRequests.STATUS, DataType.cint()));
		ListenableFuture<ResultSet> resourceFut = cassandra.execute(
				SchemaBuilder.createTable(CassandraSchema.ResourceLoadRequests.TABLE_NAME)
				.ifNotExists()
				.addPartitionKey(CassandraSchema.ResourceLoadRequests.DAY, DataType.date())
				.addClusteringColumn(CassandraSchema.ResourceLoadRequests.TIME, DataType.timestamp())
				.addClusteringColumn(CassandraSchema.ResourceLoadRequests.SPAN_ID, DataType.bigint())
				.addColumn(CassandraSchema.ResourceLoadRequests.TRACE_ID, DataType.bigint())
				.addColumn(CassandraSchema.ResourceLoadRequests.SESSION_ID, DataType.bigint())
				.addColumn(CassandraSchema.ResourceLoadRequests.TAB_ID, DataType.bigint())
				.addColumn(CassandraSchema.ResourceLoadRequests.BROWSER, DataType.text())
				.addColumn(CassandraSchema.ResourceLoadRequests.DEVICE, DataType.text())
				.addColumn(CassandraSchema.ResourceLoadRequests.LANGUAGE, DataType.text())
				.addColumn(CassandraSchema.ResourceLoadRequests.DURATION, DataType.cdouble())
				.addColumn(CassandraSchema.ResourceLoadRequests.URL, DataType.text())
				.addColumn(CassandraSchema.ResourceLoadRequests.BASE_URL, DataType.text())
				.addColumn(CassandraSchema.ResourceLoadRequests.TRANSFER_SIZE, DataType.bigint())
				.addColumn(CassandraSchema.ResourceLoadRequests.INITIATOR_TYPE, DataType.text()));
		ListenableFuture<ResultSet> pageloadFut = cassandra.execute(
				SchemaBuilder.createTable(CassandraSchema.PageLoadRequests.TABLE_NAME)
				.ifNotExists()
				.addPartitionKey(CassandraSchema.PageLoadRequests.DAY, DataType.date())
				.addClusteringColumn(CassandraSchema.PageLoadRequests.TIME, DataType.timestamp())
				.addClusteringColumn(CassandraSchema.PageLoadRequests.SPAN_ID, DataType.bigint())
				.addColumn(CassandraSchema.PageLoadRequests.TRACE_ID, DataType.bigint())
				.addColumn(CassandraSchema.PageLoadRequests.SESSION_ID, DataType.bigint())
				.addColumn(CassandraSchema.PageLoadRequests.TAB_ID, DataType.bigint())
				.addColumn(CassandraSchema.PageLoadRequests.BROWSER, DataType.text())
				.addColumn(CassandraSchema.PageLoadRequests.DEVICE, DataType.text())
				.addColumn(CassandraSchema.PageLoadRequests.LANGUAGE, DataType.text())
				.addColumn(CassandraSchema.PageLoadRequests.DURATION, DataType.cdouble())
				.addColumn(CassandraSchema.PageLoadRequests.URL, DataType.text())
				.addColumn(CassandraSchema.PageLoadRequests.RESOURCE_COUNT, DataType.cint())
				.addColumn(CassandraSchema.PageLoadRequests.SPEEDINDEX, DataType.cdouble())
				.addColumn(CassandraSchema.PageLoadRequests.FIRST_PAINT, DataType.timestamp())
				.addColumn(CassandraSchema.PageLoadRequests.UNLOAD_EVENT_START, DataType.timestamp())
				.addColumn(CassandraSchema.PageLoadRequests.UNLOAD_EVENT_END, DataType.timestamp())
				.addColumn(CassandraSchema.PageLoadRequests.REDIRECT_START, DataType.timestamp())
				.addColumn(CassandraSchema.PageLoadRequests.REDIRECT_END, DataType.timestamp())
				.addColumn(CassandraSchema.PageLoadRequests.FETCH_START, DataType.timestamp())
				.addColumn(CassandraSchema.PageLoadRequests.DOMAIN_LOOKUP_START, DataType.timestamp())
				.addColumn(CassandraSchema.PageLoadRequests.CONNECT_START, DataType.timestamp())
				.addColumn(CassandraSchema.PageLoadRequests.CONNECT_END, DataType.timestamp())
				.addColumn(CassandraSchema.PageLoadRequests.SECURE_CONNECTION_START, DataType.timestamp())
				.addColumn(CassandraSchema.PageLoadRequests.REQUEST_START, DataType.timestamp())
				.addColumn(CassandraSchema.PageLoadRequests.RESPONSE_START, DataType.timestamp())
				.addColumn(CassandraSchema.PageLoadRequests.RESPONSE_END, DataType.timestamp())
				.addColumn(CassandraSchema.PageLoadRequests.DOM_LOADING, DataType.timestamp())
				.addColumn(CassandraSchema.PageLoadRequests.DOM_INTERACTIVE, DataType.timestamp())
				.addColumn(CassandraSchema.PageLoadRequests.DOM_CONTENT_LOADED_EVENT_START, DataType.timestamp())
				.addColumn(CassandraSchema.PageLoadRequests.DOM_CONTENT_LOADED_EVENT_END, DataType.timestamp())
				.addColumn(CassandraSchema.PageLoadRequests.DOM_COMPLETE, DataType.timestamp())
				.addColumn(CassandraSchema.PageLoadRequests.LOAD_EVENT_START, DataType.timestamp()));
		ListenableFuture<ResultSet> domEventsFut = cassandra.execute(
				SchemaBuilder.createTable(CassandraSchema.RootDomEvents.TABLE_NAME)
				.ifNotExists()
				.addPartitionKey(CassandraSchema.RootDomEvents.DAY, DataType.date())
				.addClusteringColumn(CassandraSchema.RootDomEvents.TIME, DataType.timestamp())
				.addClusteringColumn(CassandraSchema.RootDomEvents.SPAN_ID, DataType.bigint())
				.addColumn(CassandraSchema.RootDomEvents.TRACE_ID, DataType.bigint())
				.addColumn(CassandraSchema.RootDomEvents.SESSION_ID, DataType.bigint())
				.addColumn(CassandraSchema.RootDomEvents.TAB_ID, DataType.bigint())
				.addColumn(CassandraSchema.RootDomEvents.BROWSER, DataType.text())
				.addColumn(CassandraSchema.RootDomEvents.DEVICE, DataType.text())
				.addColumn(CassandraSchema.RootDomEvents.LANGUAGE, DataType.text())
				.addColumn(CassandraSchema.RootDomEvents.RELEVANT_THROUGH_SELECTOR, DataType.cboolean())
				.addColumn(CassandraSchema.RootDomEvents.BASE_URL, DataType.text())
				.addColumn(CassandraSchema.RootDomEvents.ACTION_TYPE, DataType.text())
				.addColumn(CassandraSchema.RootDomEvents.ELEMENT_INFO, DataType.map(DataType.text(), DataType.text())));
		@SuppressWarnings("unchecked")
		ListenableFuture<List<ResultSet>> allFutures = Futures.allAsList(ajaxFut, pageloadFut, resourceFut, domEventsFut);
		return allFutures;
	}

	private Future<?> prepareEUMStatements() {
		ListenableFuture<PreparedStatement> ajaxFut = cassandra.prepare(
				QueryBuilder.insertInto(CassandraSchema.AjaxRequests.TABLE_NAME)
				.value(CassandraSchema.AjaxRequests.DAY, QueryBuilder.bindMarker(CassandraSchema.AjaxRequests.DAY))
				.value(CassandraSchema.AjaxRequests.TIME, QueryBuilder.bindMarker(CassandraSchema.AjaxRequests.TIME))
				.value(CassandraSchema.AjaxRequests.TRACE_ID, QueryBuilder.bindMarker(CassandraSchema.AjaxRequests.TRACE_ID))
				.value(CassandraSchema.AjaxRequests.SPAN_ID, QueryBuilder.bindMarker(CassandraSchema.AjaxRequests.SPAN_ID))
				.value(CassandraSchema.AjaxRequests.SESSION_ID, QueryBuilder.bindMarker(CassandraSchema.AjaxRequests.SESSION_ID))
				.value(CassandraSchema.AjaxRequests.TAB_ID, QueryBuilder.bindMarker(CassandraSchema.AjaxRequests.TAB_ID))
				.value(CassandraSchema.AjaxRequests.BROWSER, QueryBuilder.bindMarker(CassandraSchema.AjaxRequests.BROWSER))
				.value(CassandraSchema.AjaxRequests.DEVICE, QueryBuilder.bindMarker(CassandraSchema.AjaxRequests.DEVICE))
				.value(CassandraSchema.AjaxRequests.LANGUAGE, QueryBuilder.bindMarker(CassandraSchema.AjaxRequests.LANGUAGE))
				.value(CassandraSchema.AjaxRequests.DURATION, QueryBuilder.bindMarker(CassandraSchema.AjaxRequests.DURATION))
				.value(CassandraSchema.AjaxRequests.URL, QueryBuilder.bindMarker(CassandraSchema.AjaxRequests.URL))
				.value(CassandraSchema.AjaxRequests.BASE_URL, QueryBuilder.bindMarker(CassandraSchema.AjaxRequests.BASE_URL))
				.value(CassandraSchema.AjaxRequests.STATUS, QueryBuilder.bindMarker(CassandraSchema.AjaxRequests.STATUS)));
		ListenableFuture<PreparedStatement> resourceFut = cassandra.prepare(QueryBuilder.insertInto(CassandraSchema.ResourceLoadRequests.TABLE_NAME)
				.value(CassandraSchema.ResourceLoadRequests.DAY, QueryBuilder.bindMarker(CassandraSchema.ResourceLoadRequests.DAY))
				.value(CassandraSchema.ResourceLoadRequests.TIME, QueryBuilder.bindMarker(CassandraSchema.ResourceLoadRequests.TIME))
				.value(CassandraSchema.ResourceLoadRequests.TRACE_ID, QueryBuilder.bindMarker(CassandraSchema.ResourceLoadRequests.TRACE_ID))
				.value(CassandraSchema.ResourceLoadRequests.SPAN_ID, QueryBuilder.bindMarker(CassandraSchema.ResourceLoadRequests.SPAN_ID))
				.value(CassandraSchema.ResourceLoadRequests.SESSION_ID, QueryBuilder.bindMarker(CassandraSchema.ResourceLoadRequests.SESSION_ID))
				.value(CassandraSchema.ResourceLoadRequests.TAB_ID, QueryBuilder.bindMarker(CassandraSchema.ResourceLoadRequests.TAB_ID))
				.value(CassandraSchema.ResourceLoadRequests.BROWSER, QueryBuilder.bindMarker(CassandraSchema.ResourceLoadRequests.BROWSER))
				.value(CassandraSchema.ResourceLoadRequests.DEVICE, QueryBuilder.bindMarker(CassandraSchema.ResourceLoadRequests.DEVICE))
				.value(CassandraSchema.ResourceLoadRequests.LANGUAGE, QueryBuilder.bindMarker(CassandraSchema.ResourceLoadRequests.LANGUAGE))
				.value(CassandraSchema.ResourceLoadRequests.DURATION, QueryBuilder.bindMarker(CassandraSchema.ResourceLoadRequests.DURATION))
				.value(CassandraSchema.ResourceLoadRequests.URL, QueryBuilder.bindMarker(CassandraSchema.ResourceLoadRequests.URL))
				.value(CassandraSchema.ResourceLoadRequests.BASE_URL, QueryBuilder.bindMarker(CassandraSchema.ResourceLoadRequests.BASE_URL))
				.value(CassandraSchema.ResourceLoadRequests.TRANSFER_SIZE, QueryBuilder.bindMarker(CassandraSchema.ResourceLoadRequests.TRANSFER_SIZE))
				.value(CassandraSchema.ResourceLoadRequests.INITIATOR_TYPE, QueryBuilder.bindMarker(CassandraSchema.ResourceLoadRequests.INITIATOR_TYPE)));
		ListenableFuture<PreparedStatement> domEventsFut =  cassandra.prepare(
				QueryBuilder.insertInto(CassandraSchema.RootDomEvents.TABLE_NAME)
				.value(CassandraSchema.RootDomEvents.DAY, QueryBuilder.bindMarker(CassandraSchema.RootDomEvents.DAY))
				.value(CassandraSchema.RootDomEvents.TIME, QueryBuilder.bindMarker(CassandraSchema.RootDomEvents.TIME))
				.value(CassandraSchema.RootDomEvents.TRACE_ID, QueryBuilder.bindMarker(CassandraSchema.RootDomEvents.TRACE_ID))
				.value(CassandraSchema.RootDomEvents.SPAN_ID, QueryBuilder.bindMarker(CassandraSchema.RootDomEvents.SPAN_ID))
				.value(CassandraSchema.RootDomEvents.SESSION_ID, QueryBuilder.bindMarker(CassandraSchema.RootDomEvents.SESSION_ID))
				.value(CassandraSchema.RootDomEvents.TAB_ID, QueryBuilder.bindMarker(CassandraSchema.RootDomEvents.TAB_ID))
				.value(CassandraSchema.RootDomEvents.BROWSER, QueryBuilder.bindMarker(CassandraSchema.RootDomEvents.BROWSER))
				.value(CassandraSchema.RootDomEvents.DEVICE, QueryBuilder.bindMarker(CassandraSchema.RootDomEvents.DEVICE))
				.value(CassandraSchema.RootDomEvents.LANGUAGE, QueryBuilder.bindMarker(CassandraSchema.RootDomEvents.LANGUAGE))
				.value(CassandraSchema.RootDomEvents.RELEVANT_THROUGH_SELECTOR, QueryBuilder.bindMarker(CassandraSchema.RootDomEvents.RELEVANT_THROUGH_SELECTOR))
				.value(CassandraSchema.RootDomEvents.BASE_URL, QueryBuilder.bindMarker(CassandraSchema.RootDomEvents.BASE_URL))
				.value(CassandraSchema.RootDomEvents.ACTION_TYPE, QueryBuilder.bindMarker(CassandraSchema.RootDomEvents.ACTION_TYPE))
				.value(CassandraSchema.RootDomEvents.ELEMENT_INFO, QueryBuilder.bindMarker(CassandraSchema.RootDomEvents.ELEMENT_INFO)));
		ListenableFuture<PreparedStatement> pageloadFut = cassandra.prepare(
				QueryBuilder.insertInto(CassandraSchema.PageLoadRequests.TABLE_NAME)
				.value(CassandraSchema.PageLoadRequests.DAY, QueryBuilder.bindMarker(CassandraSchema.PageLoadRequests.DAY))
				.value(CassandraSchema.PageLoadRequests.TIME, QueryBuilder.bindMarker(CassandraSchema.PageLoadRequests.TIME))
				.value(CassandraSchema.PageLoadRequests.TRACE_ID, QueryBuilder.bindMarker(CassandraSchema.PageLoadRequests.TRACE_ID))
				.value(CassandraSchema.PageLoadRequests.SPAN_ID, QueryBuilder.bindMarker(CassandraSchema.PageLoadRequests.SPAN_ID))
				.value(CassandraSchema.PageLoadRequests.SESSION_ID, QueryBuilder.bindMarker(CassandraSchema.PageLoadRequests.SESSION_ID))
				.value(CassandraSchema.PageLoadRequests.TAB_ID, QueryBuilder.bindMarker(CassandraSchema.PageLoadRequests.TAB_ID))
				.value(CassandraSchema.PageLoadRequests.BROWSER, QueryBuilder.bindMarker(CassandraSchema.PageLoadRequests.BROWSER))
				.value(CassandraSchema.PageLoadRequests.DEVICE, QueryBuilder.bindMarker(CassandraSchema.PageLoadRequests.DEVICE))
				.value(CassandraSchema.PageLoadRequests.LANGUAGE, QueryBuilder.bindMarker(CassandraSchema.PageLoadRequests.LANGUAGE))
				.value(CassandraSchema.PageLoadRequests.DURATION, QueryBuilder.bindMarker(CassandraSchema.PageLoadRequests.DURATION))
				.value(CassandraSchema.PageLoadRequests.URL, QueryBuilder.bindMarker(CassandraSchema.PageLoadRequests.URL))
				.value(CassandraSchema.PageLoadRequests.RESOURCE_COUNT, QueryBuilder.bindMarker(CassandraSchema.PageLoadRequests.RESOURCE_COUNT))
				.value(CassandraSchema.PageLoadRequests.SPEEDINDEX, QueryBuilder.bindMarker(CassandraSchema.PageLoadRequests.SPEEDINDEX))
				.value(CassandraSchema.PageLoadRequests.FIRST_PAINT, QueryBuilder.bindMarker(CassandraSchema.PageLoadRequests.FIRST_PAINT))
				.value(CassandraSchema.PageLoadRequests.UNLOAD_EVENT_START, QueryBuilder.bindMarker(CassandraSchema.PageLoadRequests.UNLOAD_EVENT_START))
				.value(CassandraSchema.PageLoadRequests.UNLOAD_EVENT_END, QueryBuilder.bindMarker(CassandraSchema.PageLoadRequests.UNLOAD_EVENT_END))
				.value(CassandraSchema.PageLoadRequests.REDIRECT_START, QueryBuilder.bindMarker(CassandraSchema.PageLoadRequests.REDIRECT_START))
				.value(CassandraSchema.PageLoadRequests.REDIRECT_END, QueryBuilder.bindMarker(CassandraSchema.PageLoadRequests.REDIRECT_END))
				.value(CassandraSchema.PageLoadRequests.FETCH_START, QueryBuilder.bindMarker(CassandraSchema.PageLoadRequests.FETCH_START))
				.value(CassandraSchema.PageLoadRequests.DOMAIN_LOOKUP_START, QueryBuilder.bindMarker(CassandraSchema.PageLoadRequests.DOMAIN_LOOKUP_START))
				.value(CassandraSchema.PageLoadRequests.CONNECT_START, QueryBuilder.bindMarker(CassandraSchema.PageLoadRequests.CONNECT_START))
				.value(CassandraSchema.PageLoadRequests.CONNECT_END, QueryBuilder.bindMarker(CassandraSchema.PageLoadRequests.CONNECT_END))
				.value(CassandraSchema.PageLoadRequests.SECURE_CONNECTION_START, QueryBuilder.bindMarker(CassandraSchema.PageLoadRequests.SECURE_CONNECTION_START))
				.value(CassandraSchema.PageLoadRequests.REQUEST_START, QueryBuilder.bindMarker(CassandraSchema.PageLoadRequests.REQUEST_START))
				.value(CassandraSchema.PageLoadRequests.RESPONSE_START, QueryBuilder.bindMarker(CassandraSchema.PageLoadRequests.RESPONSE_START))
				.value(CassandraSchema.PageLoadRequests.RESPONSE_END, QueryBuilder.bindMarker(CassandraSchema.PageLoadRequests.RESPONSE_END))
				.value(CassandraSchema.PageLoadRequests.DOM_LOADING, QueryBuilder.bindMarker(CassandraSchema.PageLoadRequests.DOM_LOADING))
				.value(CassandraSchema.PageLoadRequests.DOM_INTERACTIVE, QueryBuilder.bindMarker(CassandraSchema.PageLoadRequests.DOM_INTERACTIVE))
				.value(CassandraSchema.PageLoadRequests.DOM_CONTENT_LOADED_EVENT_START, QueryBuilder.bindMarker(CassandraSchema.PageLoadRequests.DOM_CONTENT_LOADED_EVENT_START))
				.value(CassandraSchema.PageLoadRequests.DOM_CONTENT_LOADED_EVENT_END, QueryBuilder.bindMarker(CassandraSchema.PageLoadRequests.DOM_CONTENT_LOADED_EVENT_END))
				.value(CassandraSchema.PageLoadRequests.DOM_COMPLETE, QueryBuilder.bindMarker(CassandraSchema.PageLoadRequests.DOM_COMPLETE))
				.value(CassandraSchema.PageLoadRequests.LOAD_EVENT_START, QueryBuilder.bindMarker(CassandraSchema.PageLoadRequests.LOAD_EVENT_START)));
		@SuppressWarnings("unchecked")
		ListenableFuture<List<PreparedStatement>> allFutures = Futures.allAsList(resourceFut, ajaxFut, pageloadFut, domEventsFut);
		allFutures.addListener(() -> {
			try {
				insertAjax = ajaxFut.get();
				insertRootDomEvent = domEventsFut.get();
				insertPageLoad = pageloadFut.get();
				insertResourceLoad = resourceFut.get();
			} catch (Exception e) {
				LOG.error("Error preparing statements: ", e);
			}
		}, MoreExecutors.directExecutor());
		return allFutures;
	}


}
