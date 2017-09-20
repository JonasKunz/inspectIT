package rocks.inspectit.server.cassandra;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Cluster.Builder;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.RegularStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import rocks.inspectit.server.externalservice.IExternalService;
import rocks.inspectit.shared.all.cmr.property.spring.PropertyUpdate;
import rocks.inspectit.shared.all.externalservice.ExternalServiceStatus;
import rocks.inspectit.shared.all.externalservice.ExternalServiceType;

/**
 * @author Jonas Kunz
 *
 *         Manages the connection to a cassandra cluster
 *
 */

@Component
public class CassandraDao implements IExternalService {

	private static final int PING_FREQUENCY = 5000;

	private static final Logger LOG = LoggerFactory.getLogger(CassandraDao.class);

	private static final int MAX_PARALLEL_QUERIES = 500;

	private static final int MAX_QUEUE_SIZE = MAX_PARALLEL_QUERIES * 10;

	@Value("${cassandra.active}")
	boolean active;

	@Value("${cassandra.keyspace}")
	String keyspaceName;

	@Value("${cassandra.hosts}")
	String hosts;

	@Value("${cassandra.port}")
	int port;

	@Value("${cassandra.user}")
	String user;

	@Value("${cassandra.passwd}")
	String password;

	@Value("${cassandra.ssl}")
	boolean useSSL;

	/**
	 * The retention policy to use.
	 */
	@Value("${influxdb.retentionPolicy}")
	String retentionPolicy;

	@Autowired
	@Resource(name = "scheduledExecutorService")
	private ScheduledExecutorService scheduledExecutorService;

	private Cluster cluster;
	private Session session;

	private volatile boolean isConnected = false;

	private BlockingQueue<QueryTask> queryQueue;
	private Semaphore queryExecutorPool;
	private Set<QueryTask> runningQueries;

	private Thread queryExecutor;

	private Future<?> connectionTask;

	private List<CassandraConnectionStateListener> listeners;

	@PostConstruct
	void init() {

		runningQueries = ConcurrentHashMap.<QueryTask> newKeySet();
		queryQueue = new ArrayBlockingQueue<>(MAX_QUEUE_SIZE, false);
		queryExecutorPool = new Semaphore(MAX_PARALLEL_QUERIES, false);
		listeners = new ArrayList<>();

		propertiesUpdated();

	}


	/**
	 * Connects to the InfluxDB if the feature has been enabled.
	 */
	@PostConstruct
	@PropertyUpdate(properties = { "cassandra.hosts", "cassandra.port", "cassandra.user", "cassandra.passwd", "cassandra.keyspace", "cassandra.active", "cassandra.ssl" })
	public void propertiesUpdated() {
		if (connectionTask != null) {
			connectionTask.cancel(false);
			try {
				connectionTask.get();
			} catch (Exception e) {
				LOG.trace("connectionTask terminated", e);
			}
		}
		disconnect();
		if (active) {
			connectionTask = scheduledExecutorService.submit(this::connect);
		}
	}

	private synchronized void connect() {
		try {
			Builder connectionBuilder = Cluster.builder();
			connectionBuilder.withPort(port);
			for (String host : hosts.split(",")) {
				connectionBuilder.addContactPoint(host);
			}
			if (useSSL) {
				connectionBuilder.withSSL();
			}
			if (!user.isEmpty()) {
				connectionBuilder.withCredentials(user, password);
			}
			cluster = connectionBuilder.build();
			session = cluster.connect();
			session.execute("CREATE KEYSPACE IF NOT EXISTS " + keyspaceName + " WITH REPLICATION = {'class' : 'SimpleStrategy', 'replication_factor' : 1}");
			session.execute("USE " + keyspaceName);
			isConnected = true;
			startQueryExecutor();
			LOG.info("Connected to Cassandra!");
			listeners.forEach((l) -> l.connected(this));
		} catch (Exception e) {
			LOG.error("Error connecting to Cassandra!", e);
			isConnected = false;
			if (session != null) {
				session.close();
			}
			if (cluster != null) {
				cluster.close();
			}
		}
	}

	public ListenableFuture<ResultSet> execute(Statement query) {
		QueryTask task = new QueryTask(query);
		if (!isConnected) {
			task.resultFuture.setException(new IllegalStateException("Cassandra is not connected!"));
		} else if (!queryQueue.offer(task)) {
			LOG.error("Cancelling query, Queue is full!");
			task.resultFuture.setException(new IllegalStateException("Query-Queue is full, cannot issue query without overloading Cassandra!"));
		}
		return Futures.dereference(task.resultFuture);
	}

	public ListenableFuture<PreparedStatement> prepare(RegularStatement query) {
		return session.prepareAsync(query);
	}

	private void startQueryExecutor() {

		queryExecutor = new Thread(() -> {
			try {
				while (true) {
					QueryTask task = queryQueue.take();
					queryExecutorPool.acquire();
					// start the query
					ResultSetFuture resultFuture = session.executeAsync(task.query);
					runningQueries.add(task);
					// add cleanup mechanism
					resultFuture.addListener(() -> {
						runningQueries.remove(task);
						queryExecutorPool.release();
					}, MoreExecutors.directExecutor());
					// update the nested future to let clients listen to it
					task.resultFuture.set(resultFuture);

				}
			} catch (InterruptedException e) {
				LOG.trace("Terminating Cassandra Query Executor.");
			} catch (Exception e) {
				LOG.error("Error in query executor", e);
			}
		});
		queryExecutor.start();
	}

	synchronized void disconnect() {
		if (isConnected) {
			isConnected = false;
			try {
				// prevent execution of new queries
				queryExecutor.interrupt();
				try {
					queryExecutor.join();
				} catch (InterruptedException e) {
					LOG.error("Waiting for query executor was interrupted!", e);
				}
				// terminate running queries
				for (QueryTask runningQuery : runningQueries) {
					try {
						runningQuery.resultFuture.get().cancel(true);
					} catch (ExecutionException | InterruptedException e) {
						LOG.error("Error cancelling query", e);
					}
				}
			} finally {
				session.close();
				cluster.close();
			}
			listeners.forEach((l) -> l.disconnected(this));
		}

	}

	public boolean isConnected() {
		return isConnected;
	}

	public void addConnectionStateListener(CassandraConnectionStateListener lis) {
		listeners.add(lis);
	}

	public void removeConnectionStateListener(CassandraConnectionStateListener lis) {
		listeners.remove(lis);
	}

	private static class QueryTask {

		Statement query;
		SettableFuture<ResultSetFuture> resultFuture;

		public QueryTask(Statement query) {
			this.query = query;
			this.resultFuture = SettableFuture.create();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ExternalServiceStatus getServiceStatus() {
		if (active) {
			if (isConnected && !session.getState().getConnectedHosts().isEmpty()) {
				return ExternalServiceStatus.CONNECTED;
			} else {
				return ExternalServiceStatus.DISCONNECTED;
			}
		} else {
			return ExternalServiceStatus.DISABLED;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ExternalServiceType getServiceType() {
		return ExternalServiceType.CASSANDRA;
	}

}
