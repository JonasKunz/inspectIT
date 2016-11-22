package rocks.inspectit.agent.java.eum.data;

import java.util.Random;

import org.springframework.stereotype.Component;

/**
 * Component responsible for generating unique EUM IDs.
 *
 * @author Jonas Kunz
 *
 */
@Component
public class IdGenerator {

	/**
	 * Random to use.
	 */
	private Random rnd = new Random();

	/**
	 * @return a new unique session ID.
	 */
	public long generateSessionID() {
		return rnd.nextLong();
	}

	/**
	 * @return a new unique tab ID.
	 */
	public long generateTabID() {
		return rnd.nextLong();
	}

}
