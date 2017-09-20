package rocks.inspectit.server.cassandra;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

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

	private PreparedStatement insertAjax;
	private PreparedStatement insertPageLoad;
	private PreparedStatement insertRootDomEvent;

	private boolean isSchemaSetUp = false;

	private CassandraConnectionStateListener connectionListener = new CassandraConnectionStateListener() {
		@Override
		public void disconnected(CassandraDao cassandra) {
			isSchemaSetUp = false;
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
		if (isSchemaSetUp) {
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


	public void insertRootDomEvent(Optional<UserSessionInfo> sessionInfo, JSDomEvent action) {
		if (isSchemaSetUp) {
			EUMSpan span = action.getOwningSpan();
			BoundStatement insert = insertRootDomEvent.bind()
					.setDate(CassandraSchema.RootDomEvents.DAY, LocalDate.fromMillisSinceEpoch(span.getTimeStamp().getTime()))
					.setTimestamp(CassandraSchema.RootDomEvents.TIME, span.getTimeStamp())
					.setLong(CassandraSchema.RootDomEvents.TRACE_ID, span.getSpanIdent().getTraceId())
					.setLong(CassandraSchema.RootDomEvents.SPAN_ID, span.getSpanIdent().getId())
					.setLong(CassandraSchema.RootDomEvents.SESSION_ID, span.getSessionId())
					.setLong(CassandraSchema.RootDomEvents.TAB_ID, span.getTabId())
					.setDouble(CassandraSchema.RootDomEvents.DURATION, span.getDuration())
					.setString(CassandraSchema.RootDomEvents.BASE_URL, action.getBaseUrl())
					.setString(CassandraSchema.RootDomEvents.ACTION_TYPE, action.getEventType())
					.setMap(CassandraSchema.RootDomEvents.ELEMENT_INFO, action.getElementInfo());
			sessionInfo.ifPresent((s) -> addSessionInfoToInsert(s, insert));
			listenForErrors(cassandra.execute(insert));
		}
	}

	public void insertPageload(Optional<UserSessionInfo> sessionInfo, PageLoadRequest load) {
		if (isSchemaSetUp) {
			EUMSpan span = load.getOwningSpan();
			BoundStatement insert = insertPageLoad.bind()
					.setDate(CassandraSchema.PageLoadRequests.DAY, LocalDate.fromMillisSinceEpoch(span.getTimeStamp().getTime()))
					.setTimestamp(CassandraSchema.PageLoadRequests.TIME, span.getTimeStamp())
					.setLong(CassandraSchema.PageLoadRequests.TRACE_ID, span.getSpanIdent().getTraceId())
					.setLong(CassandraSchema.PageLoadRequests.SPAN_ID, span.getSpanIdent().getId())
					.setLong(CassandraSchema.PageLoadRequests.SESSION_ID, span.getSessionId())
					.setLong(CassandraSchema.PageLoadRequests.TAB_ID, span.getTabId())
					.setDouble(CassandraSchema.PageLoadRequests.DURATION, span.getDuration())
					.setString(CassandraSchema.PageLoadRequests.URL, load.getUrl());
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
			} catch (InterruptedException | ExecutionException e) {
				LOG.error("Error executing query!", e);
			}
		}, MoreExecutors.directExecutor());
	}

	private void initSchema() {
		try {
			initEUMTables().get();
			prepareEUMStatements().get();
			isSchemaSetUp = true;
		} catch (Exception e) {
			LOG.error("Error setting up schema", e);
			isSchemaSetUp = false;
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
				.addColumn(CassandraSchema.PageLoadRequests.URL, DataType.text()));
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
				.addColumn(CassandraSchema.RootDomEvents.DURATION, DataType.cdouble())
				.addColumn(CassandraSchema.RootDomEvents.BASE_URL, DataType.text())
				.addColumn(CassandraSchema.RootDomEvents.ACTION_TYPE, DataType.text())
				.addColumn(CassandraSchema.RootDomEvents.ELEMENT_INFO, DataType.map(DataType.text(), DataType.text())));
		@SuppressWarnings("unchecked")
		ListenableFuture<List<ResultSet>> allFutures = Futures.allAsList(ajaxFut, pageloadFut, domEventsFut);
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
				.value(CassandraSchema.RootDomEvents.DURATION, QueryBuilder.bindMarker(CassandraSchema.RootDomEvents.DURATION))
				.value(CassandraSchema.RootDomEvents.BASE_URL, QueryBuilder.bindMarker(CassandraSchema.RootDomEvents.BASE_URL))
				.value(CassandraSchema.RootDomEvents.ACTION_TYPE, QueryBuilder.bindMarker(CassandraSchema.RootDomEvents.ACTION_TYPE))
				.value(CassandraSchema.RootDomEvents.ELEMENT_INFO, QueryBuilder.bindMarker(CassandraSchema.RootDomEvents.ELEMENT_INFO)));
		ListenableFuture<PreparedStatement> pageloadFut = cassandra
				.prepare(
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
						.value(CassandraSchema.PageLoadRequests.URL, QueryBuilder.bindMarker(CassandraSchema.PageLoadRequests.URL)));

		@SuppressWarnings("unchecked")
		ListenableFuture<List<PreparedStatement>> allFutures = Futures.allAsList(ajaxFut, pageloadFut, domEventsFut);
		allFutures.addListener(() -> {
			try {
				insertAjax = ajaxFut.get();
				insertRootDomEvent = domEventsFut.get();
				insertPageLoad = pageloadFut.get();
			} catch (Exception e) {
				LOG.error("Error preparing statements: ", e);
			}
		}, MoreExecutors.directExecutor());
		return allFutures;
	}


}