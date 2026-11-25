package repro;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.mpr.LegacyWrapper;

@Route("")
public class StartView extends VerticalLayout {

    // Sends the request that vaadinBootstrap.js (fetchRootConfig) already sent once for this page.
    // The response is ignored, so the client-side state of the legacy application stays untouched.
    private static final String RESEND_ON_CLICK = """
            this.addEventListener('click', async () => {
              const appId = window.mprUiId;
              const app = window.vaadin.getApp(appId);
              let url = app.getConfig('browserDetailsUrl');
              url += (/\\?/.test(url) ? '&' : '?') + 'v-' + Date.now();
              let params = 'v-browserDetails=1';
              const theme = app.getConfig('theme');
              if (theme !== undefined) {
                params += '&theme=' + encodeURIComponent(theme);
              }
              params += '&v-appId=' + appId;
              const extraParams = app.getConfig('extraParams');
              if (extraParams !== undefined) {
                params += extraParams;
              }
              params += '&' + window.vaadin.getBrowserDetailsParameters(appId, app.getConfig('sendUrlsAsParameters'));
              const response = await fetch(url, {
                method: 'POST',
                headers: {'Content-type': 'application/x-www-form-urlencoded'},
                body: params
              });
              $0.textContent = 'Legacy init request sent again, HTTP status ' + response.status;
            });
            """;

    private int flowRoundTrips;
    private int legacyRoundTrips;

    public StartView() {
        com.vaadin.ui.Label legacyInfo = new com.vaadin.ui.Label("Legacy round trips: 0");
        com.vaadin.ui.Button legacyButton = new com.vaadin.ui.Button("Legacy round trip",
                event -> legacyInfo.setValue("Legacy round trips: " + ++legacyRoundTrips));
        legacyButton.setId("legacy-round-trip");
        com.vaadin.ui.VerticalLayout legacyContent = new com.vaadin.ui.VerticalLayout(legacyButton, legacyInfo);

        Span resendStatus = new Span("Legacy init request not sent again yet");
        resendStatus.setId("resend-status");
        Button resend = new Button("Send legacy init request again");
        resend.setId("resend");
        resend.getElement().executeJs(RESEND_ON_CLICK, resendStatus.getElement());

        Span flowInfo = new Span("Flow round trips: 0");
        flowInfo.setId("flow-info");
        Button flowButton = new Button("Flow round trip",
                event -> flowInfo.setText("Flow round trips: " + ++flowRoundTrips));
        flowButton.setId("flow-round-trip");

        add(new LegacyWrapper(legacyContent), flowButton, flowInfo, resend, resendStatus);
    }
}
