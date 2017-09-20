package rocks.inspectit.server.cassandra;

/**
 * @author Jonas Kunz
 *
 */
public interface CassandraConnectionStateListener {

	void disconnected(CassandraDao cassandra);

	void connected(CassandraDao cassandra);

}
