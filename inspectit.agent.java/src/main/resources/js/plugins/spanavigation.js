
/**
 * Instruments Navigation Changes which are either done through URL hashes or through the HTML 5 history API.
 */

window.inspectIT.registerPlugin("SPANavigation", (function() {
	
	var inspectIT = window.inspectIT;
	
	var lastURL = window.location.href;
	var lastUrlChangeEvent;
	
	function urlChanged(newURL) {
		lastUrlChangeEvent = inspectIT.createEUMElement("urlchange");
		lastUrlChangeEvent.lastURL = lastURL;
		lastUrlChangeEvent.newURL = newURL;
		lastURL = newURL;
	}
	
	function initInstrumentation() {
		//instrument push state
		if("history" in window ) {
			if("pushState" in window.history) {
				var originalPushState = window.history.pushState;
				window.history.pushState = function(stateObj,title,newURL) {
					var args = arguments;
					var result;
					urlChanged(newURL);
					lastUrlChangeEvent.buildTrace(true, function() {
						result = originalPushState.apply(window.history, args);
					});
					lastUrlChangeEvent.initiator = "PUSHSTATE";
					lastUrlChangeEvent.markRelevant();
					return result;
				}
			}
			if("replaceState" in window.history) {
				var originalReplaceState = window.history.replaceState;
				window.history.replaceState = function(stateObj,title,newURL) {
					var args = arguments;
					var result;
					urlChanged(newURL);
					lastUrlChangeEvent.buildTrace(true, function() {
						result = originalReplaceState.apply(window.history, args);
					});
					lastUrlChangeEvent.initiator = "REPLACESTATE";
					lastUrlChangeEvent.markRelevant();
					return result;
				}
			}
			inspectIT.instrumentation.runWithout(function() {
				window.addEventListener("popstate",function(event) {
					var newURL = window.location.href;
					if(newURL != lastURL) {
						urlChanged(newURL);
						lastUrlChangeEvent.buildTrace(true);
						lastUrlChangeEvent.initiator = "NAVIGATION";
						lastUrlChangeEvent.markRelevant();						
					}
				});
				window.addEventListener("hashchange",function(event) {
					var newURL = window.location.href;
					if(newURL != lastURL) {
						urlChanged(newURL);
						lastUrlChangeEvent.buildTrace(true);
						lastUrlChangeEvent.initiator = "NAVIGATION";
						lastUrlChangeEvent.markRelevant();						
					}
				});
			});
		}
		
		var urlObserver = (function() {
			function testForUrlChange(element) {
				var newURL = window.location.href;
				if(newURL != lastURL) {
					urlChanged(newURL);
					lastUrlChangeEvent.buildTrace(true);
					lastUrlChangeEvent.initiator = "HASHMODIFIED";
					lastUrlChangeEvent.markRelevant();						
				}
			}
			return {
				preElementBegin : testForUrlChange,
				preElementFinish : testForUrlChange
			}
		})();
		
		inspectIT.traceBuilder.addTraceObserver(urlObserver);
	}
	return {
		init : initInstrumentation
	}
})());