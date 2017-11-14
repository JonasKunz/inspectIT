package rocks.inspectit.server.cassandra;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import rocks.inspectit.shared.all.testbase.TestBase;

/**
 * @author Jonas Kunz
 *
 */
public class CassandraDaoTest extends TestBase {

	@Mock
	CassandraClusterFactory clusterFactory;

	@Mock
	ScheduledExecutorService syncExecutorService;

	@InjectMocks
	CassandraDao dao;

	Cluster cluster;

	Session session;


	@BeforeMethod
	public void setup() {
		cluster = mock(Cluster.class);
		session = mock(Session.class);
		when(clusterFactory.connectToCluster()).thenReturn(cluster);
		when(cluster.connect()).thenReturn(session);

		when(syncExecutorService.submit(any(Runnable.class))).thenAnswer(new Answer<Future<?>>() {
			@Override
			public Future<?> answer(InvocationOnMock invocation) throws Throwable {
				Runnable runnable = (Runnable) invocation.getArguments()[0];
				SettableFuture<Object> future = SettableFuture.create();
				if (runnable != null) {
					runnable.run();
				}
				future.set(null);
				return future;
			}
		});

		when(syncExecutorService.submit(any(Runnable.class))).thenAnswer(new Answer<Future<?>>() {
			@Override
			public Future<?> answer(InvocationOnMock invocation) throws Throwable {
				Runnable runnable = (Runnable) invocation.getArguments()[0];
				SettableFuture<Object> future = SettableFuture.create();
				if (runnable != null) {
					runnable.run();
				}
				future.set(null);
				return future;
			}
		});

	}

	public static class Disconnect extends CassandraDaoTest {

		@Test
		public void cancelPendingQueriesTest() throws InterruptedException {

			dao.active = true;
			dao.init();

			final AtomicBoolean queryCalled = new AtomicBoolean(false);

			ResultSetFuture rsfut = mock(ResultSetFuture.class);
			when(session.executeAsync(any(Statement.class))).then((invocation) -> {
				synchronized (queryCalled) {
					queryCalled.set(true);
					queryCalled.notify();
				}
				return rsfut;
			});
			dao.execute(mock(Statement.class));

			// wait for the query to be processed
			synchronized (queryCalled) {
				while (!queryCalled.get()) {
					queryCalled.wait();
				}
			}

			dao.active = false;
			dao.propertiesUpdated();

			verify(rsfut, times(1)).cancel(true);

		}
	}

	public static class Execute extends CassandraDaoTest {

		@AfterMethod
		public void cleanup() {
			dao.active = false;
			dao.propertiesUpdated();

		}

		@Test
		public void testResultPassThrough() throws Throwable {
			dao.active = true;
			dao.init();

			ResultSet res = Mockito.mock(ResultSet.class);
			ResultSetFuture rsfut = mock(ResultSetFuture.class);
			when(rsfut.get()).thenReturn(res);
			when(session.executeAsync(any(Statement.class))).thenReturn(rsfut);
			Mockito.doAnswer(invocation -> {
				((Runnable) invocation.getArguments()[0]).run();
				return null;
			}).when(rsfut).addListener(any(Runnable.class), any(Executor.class));

			try {
				assertThat(dao.execute(mock(Statement.class)).get(), equalTo(res));
			} catch (ExecutionException e) {
				throw e.getCause();
			}

		}

		@Test(expectedExceptions = { IOException.class })
		public void testErrorPassThrough() throws Throwable {
			dao.active = true;
			dao.init();

			IOException myException = new IOException();
			ResultSetFuture rsfut = mock(ResultSetFuture.class);
			when(rsfut.get()).thenThrow(new ExecutionException(myException));
			when(session.executeAsync(any(Statement.class))).thenReturn(rsfut);
			Mockito.doAnswer(invocation -> {
				((Runnable) invocation.getArguments()[0]).run();
				return null;
			}).when(rsfut).addListener(any(Runnable.class), any(Executor.class));

			try {
				dao.execute(mock(Statement.class)).get();
			} catch (ExecutionException e) {
				throw e.getCause();
			}

		}

		@Test(expectedExceptions = { IllegalStateException.class })
		public void testNotConnected() throws Throwable {
			dao.active = false;
			dao.init();
			try {
				dao.execute(mock(Statement.class)).get();
			} catch (ExecutionException e) {
				Mockito.verifyZeroInteractions(clusterFactory, cluster, session);
				throw e.getCause();
			}
		}

		@Test(expectedExceptions = { IllegalStateException.class })
		public void testOverloadCassandra() throws Throwable {

			// +2 because one gets takn by the query executor thread
			final int OVERLOAD_COUNT = CassandraDao.MAX_PARALLEL_QUERIES + CassandraDao.MAX_QUEUE_SIZE + 2;

			dao.active = true;
			dao.init();

			ResultSetFuture rsfut = mock(ResultSetFuture.class);
			when(rsfut.get()).thenAnswer(invocation -> {
				throw new RuntimeException("Not yet handled by test!");
			});
			when(session.executeAsync(any(Statement.class))).thenReturn(rsfut);

			try {
				ListenableFuture<ResultSet> lastRunFuture = null;
				for (int i = 0; i < OVERLOAD_COUNT; i++) {
					lastRunFuture = dao.execute(mock(Statement.class));
				}
				lastRunFuture.get();
			} catch (ExecutionException e) {
				throw e.getCause();
			}
		}
	}

}