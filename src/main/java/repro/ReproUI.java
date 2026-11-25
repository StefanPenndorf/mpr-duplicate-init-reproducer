package repro;

import com.vaadin.mpr.MprUI;
import com.vaadin.server.VaadinRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Behaves like {@link MprUI} apart from logging, unless a system property switches on one of the
 * application-side attempts to cope with a second init request:
 * <ul>
 * <li>{@code -Drepro.guard=true}: ignore the second {@code doInit}. Does not help, the UI is closed
 * anyway.</li>
 * <li>{@code -Drepro.workaround=embedId}: the guard plus no embed id, so that
 * {@code VaadinSession.addUI} cannot close the UI.</li>
 * <li>{@code -Drepro.workaround=provider}: see {@link RefreshingMprUIProvider}.</li>
 * </ul>
 * Both workarounds repaint the UI once more after the response, unless started with
 * {@code -Drepro.repaint=false}.
 */
public class ReproUI extends MprUI {

    private static final Logger LOG = LoggerFactory.getLogger(ReproUI.class);

    @Override
    public void doInit(VaadinRequest request, int uiId, String embedId) {
        LOG.info("REPRO: doInit of legacy UI instance {} as UI id {} for Flow UI {}",
                Integer.toHexString(System.identityHashCode(this)), uiId, request.getParameter("ui"));
        if (isGuardEnabled() && getUIId() != -1) {
            LOG.warn("REPRO guard: UI {} is already initialized, ignoring doInit for UI id {}", getUIId(), uiId);
            if (isEmbedIdWorkaroundEnabled()) {
                repaintAfterResponse();
            }
            return;
        }
        super.doInit(request, uiId, embedId);
    }

    @Override
    public String getEmbedId() {
        return isEmbedIdWorkaroundEnabled() ? null : super.getEmbedId();
    }

    @Override
    protected void refresh(VaadinRequest request) {
        LOG.warn("REPRO: legacy UI {} received another init request and is refreshed", getUIId());
        repaintAfterResponse();
    }

    @Override
    public void close() {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        LOG.warn("REPRO: legacy UI {} is being closed by {} <- {}", getUIId(), stack[1], stack[2]);
        super.close();
    }

    // The response to the second init request takes a sync id. A client that has already processed
    // the first response never sees it and would wait for it forever. Repainting once more after
    // the response turns the next message into a resynchronization, which every client accepts.
    private void repaintAfterResponse() {
        if ("false".equals(System.getProperty("repro.repaint"))) {
            return;
        }
        access(() -> {
            LOG.warn("REPRO: repainting legacy UI {} after the second init request", getUIId());
            getSession().getCommunicationManager().repaintAll(this);
        });
    }

    private static boolean isGuardEnabled() {
        return Boolean.getBoolean("repro.guard") || isEmbedIdWorkaroundEnabled();
    }

    private static boolean isEmbedIdWorkaroundEnabled() {
        return "embedId".equals(System.getProperty("repro.workaround"));
    }
}
