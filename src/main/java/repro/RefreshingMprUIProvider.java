package repro;

import com.vaadin.mpr.MprUIProvider;
import com.vaadin.server.UICreateEvent;

/**
 * Makes {@code UIInitHandler} treat a second init request for the same embed id like a browser
 * refresh of a preserved UI: it calls {@code doRefresh} on the existing UI instead of {@code doInit}
 * and {@code addUI}. The embed id contains the MPR content id, which is unique per Flow UI, so
 * only a repeated request for the same Flow UI finds an existing UI.
 */
public class RefreshingMprUIProvider extends MprUIProvider {

    @Override
    public boolean isPreservedOnRefresh(UICreateEvent event) {
        return true;
    }
}
