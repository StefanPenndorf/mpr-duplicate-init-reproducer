package repro;

import com.vaadin.server.ServiceInitEvent;
import com.vaadin.server.VaadinServiceInitListener;

/**
 * Registered through {@code META-INF/services}. Only active with
 * {@code -Drepro.workaround=provider}.
 */
public class LegacyServiceInitListener implements VaadinServiceInitListener {

    @Override
    public void serviceInit(ServiceInitEvent event) {
        if ("provider".equals(System.getProperty("repro.workaround"))) {
            event.getSource().addSessionInitListener(
                    sessionInit -> sessionInit.getSession().addUIProvider(new RefreshingMprUIProvider()));
        }
    }
}
