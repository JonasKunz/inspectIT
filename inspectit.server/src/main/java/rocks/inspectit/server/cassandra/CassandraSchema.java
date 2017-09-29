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

	public interface ResourceLoadRequests extends EumTable {
		String TABLE_NAME = "resourceload_requests";

		String DURATION = "duration";
		String URL = "url";
		String BASE_URL = "base_url";

		String INITIATOR_TYPE = "initiator_type";
		String TRANSFER_SIZE = "transfer_size";
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
		String RESOURCE_COUNT = "resource_count";

		String SPEEDINDEX = "speedindex";
		String FIRST_PAINT = "first_paint";

		/// navtimings, navigation start and loadEventEnd are repsented by time and duration
		String UNLOAD_EVENT_START = "unload_event_start";
		String UNLOAD_EVENT_END = "unload_event_end";
		String REDIRECT_START = "redirect_start";
		String REDIRECT_END = "redirect_end";
		String FETCH_START = "fetch_start";
		String DOMAIN_LOOKUP_START = "domain_lookup_start";
		String CONNECT_START = "connect_start";
		String CONNECT_END = "connect_end";
		String SECURE_CONNECTION_START = "secure_connection_start";
		String REQUEST_START = "request_start";
		String RESPONSE_START = "response_start";
		String RESPONSE_END = "response_end";
		String DOM_LOADING = "dom_loading";
		String DOM_INTERACTIVE = "dom_interactive";
		String DOM_CONTENT_LOADED_EVENT_START = "dom_content_loaded_event_start";
		String DOM_CONTENT_LOADED_EVENT_END = "dom_content_loaded_event_end";
		String DOM_COMPLETE = "dom_complete";
		String LOAD_EVENT_START = "load_event_start";
	}

}
