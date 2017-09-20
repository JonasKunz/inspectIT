package rocks.inspectit.server.cassandra;

/**
 * @author Jonas Kunz
 *
 */
public interface CassandraSchema {

	public interface EumTable {

		String DAY = "day";
		String TIME = "time";
		String TRACE_ID = "trace_id";
		String SPAN_ID = "span_id";
		String SESSION_ID = "session_id";
		String TAB_ID = "tab_id";
		String BROWSER = "browser";
		String DEVICE = "device";
		String LANGUAGE = "language";

	}

	public interface AjaxRequests extends EumTable {
		String TABLE_NAME = "ajax_requests";

		String DURATION = "duration";
		String URL = "url";
		String BASE_URL = "base_url";
		String STATUS = "status";
	}

	public interface RootDomEvents extends EumTable {
		String TABLE_NAME = "root_dom_events";

		String DURATION = "duration";
		String BASE_URL = "base_url";
		String ACTION_TYPE = "action_type";
		String ELEMENT_INFO = "element_info";
	}

	public interface PageLoadRequests extends EumTable {
		String TABLE_NAME = "pageload_requests";

		String URL = "url";
		String DURATION = "duration";
	}

}
