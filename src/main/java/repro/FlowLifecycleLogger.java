package repro;

import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.shared.communication.PushMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Logs the life cycle of Flow sessions and UIs. With {@code -Drepro.push=true} every Flow UI uses
 * automatic push.
 */
@Component
public class FlowLifecycleLogger implements VaadinServiceInitListener {

    private static final Logger LOG = LoggerFactory.getLogger(FlowLifecycleLogger.class);

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addSessionInitListener(sessionInit -> LOG.info("REPRO: Flow session {} created",
                Integer.toHexString(System.identityHashCode(sessionInit.getSession()))));
        event.getSource().addSessionDestroyListener(sessionDestroy -> LOG.info("REPRO: Flow session {} destroyed",
                Integer.toHexString(System.identityHashCode(sessionDestroy.getSession()))));
        event.getSource().addUIInitListener(uiInit -> {
            if (Boolean.getBoolean("repro.push")) {
                uiInit.getUI().getPushConfiguration().setPushMode(PushMode.AUTOMATIC);
            }
            LOG.info("REPRO: Flow UI {} created in Flow session {}", uiInit.getUI().getUIId(),
                    Integer.toHexString(System.identityHashCode(uiInit.getUI().getSession())));
            uiInit.getUI().addDetachListener(detach -> LOG.info("REPRO: Flow UI {} detached, closing={}",
                    detach.getUI().getUIId(), detach.getUI().isClosing()));
        });
    }
}
