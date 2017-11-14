package rocks.inspectit.server.cassandra;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Cluster.Builder;

/**
 * @author Jonas Kunz
 *
 */
@Component
public class CassandraClusterFactory {

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

	public Cluster connectToCluster() {
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
		return connectionBuilder.build();
	}
}
