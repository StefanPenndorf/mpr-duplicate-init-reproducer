# MPR: a repeated legacy init request throws "This UI instance is already initialized" and closes the UI

When the Vaadin 8 init request (`POST /framework?v-…` with `v-browserDetails=1`) reaches the server
twice for the same Flow UI, MPR logs an `IllegalStateException` and closes both the legacy UI and
the Flow UI. The user loses the state of the page: the next Flow round trip reloads it.

Same symptom as [vaadin/multiplatform-runtime#54](https://github.com/vaadin/multiplatform-runtime/issues/54)
(closed 2019), where a re-sent `v-browserDetails` request was listed as a scenario that could not
be ruled out.

## Versions

- Java 21, Spring Boot 4.1.1 (embedded Tomcat 11.0.24)
- Vaadin Platform 25.3.5 (Flow 25.3.7)
- `com.vaadin:mpr-v8` 8.0.1 and 8.0.2 (both affected)
- Vaadin 8 Framework 8.32.3 (`vaadin-server-mpr-jakarta`, `vaadin-themes`)

## Setup

```
mvn package -DskipTests        # also compiles the AppWidgetset
mvn spring-boot:run            # dev mode, http://localhost:8091/
```

To test MPR 8.0.2 with a freshly compiled widgetset:

```
mvn clean package -DskipTests -Dmpr.version=8.0.2
mvn spring-boot:run -Dmpr.version=8.0.2
```

## What the app contains

- `StartView` (`""`): a Flow view with a `LegacyWrapper` (Vaadin 8 button and label), a Flow button
  and a button that sends the legacy init request again.
- `ReproUI`: `MprUI` subclass registered with `@LegacyUI`. It logs `doInit` and `close()` and
  contains the optional application-side workarounds (see below).
- `RefreshingMprUIProvider`, `LegacyServiceInitListener`: second workaround, off by default.
- `ResendFirstInitRequestFilter`: optional, see "Variant".
- `FlowLifecycleLogger`: logs Flow sessions and UIs; enables push with `-Drepro.push=true`.

## Steps to reproduce

1. Open http://localhost:8091/.
2. Click "Flow round trip" and "Legacy round trip". Both counters show 1.
3. Click "Send legacy init request again". The button builds the same request as `fetchRootConfig`
   in `vaadinBootstrap.js` and sends it with `fetch`; the response is ignored.
4. Click "Flow round trip".

## Expected

The second init request for an already initialized legacy UI is answered without an error, and the
page keeps working.

## Actual

After step 3 the server log shows (MPR 8.0.1; identical with 8.0.2):

```
WARN  repro.ReproUI : REPRO: legacy UI 1 is being closed by com.vaadin.server.VaadinSession.addUI(VaadinSession.java:1237) <- com.vaadin.server.communication.UIInitHandler.getBrowserDetailsUI(UIInitHandler.java:232)
ERROR com.vaadin.server.DefaultErrorHandler :

java.lang.IllegalStateException: This UI instance is already initialized (as UI id 1) and can therefore not be initialized again (as UI id 2). Please make sure you are not accidentally reusing an old UI instance.
	at com.vaadin.ui.UI.doInit(UI.java:767)
	at repro.ReproUI.doInit(ReproUI.java:23)
	at com.vaadin.server.communication.UIInitHandler.getBrowserDetailsUI(UIInitHandler.java:228)
	at com.vaadin.server.communication.UIInitHandler.synchronizedHandleRequest(UIInitHandler.java:86)
	at com.vaadin.mpr.core.MprUIInitHandler.synchronizedHandleRequest(MprUIInitHandler.java:88)
	...
WARN  repro.ReproUI : REPRO: legacy UI 1 is being closed by com.vaadin.mpr.core.AbstractMprUI.lambda$init$5df2f1e0$1(AbstractMprUI.java:71) <- ...
```

Step 4 reloads the page: both counters are back at 0 and the page has a new Flow UI id.

## Analysis

- `AbstractMprUIProvider.createInstance` returns the legacy UI stored in the HTTP session under
  `ui.<flowUiId>`, so a second init request for the same Flow UI gets the instance that the first
  request already initialized.
- `UIInitHandler.getBrowserDetailsUI` does not find a UI to preserve and calls `doInit` on that
  instance, which throws.
- It then calls `session.addUI(ui)`. The embed id is already mapped to this UI, so `addUI` treats
  the UI as its own predecessor and calls `close()` on it. `AbstractMprUI.close()` also closes the
  Flow UI.

Without MPR the same repeated request is harmless as far as the code shows (not tested here):
`UIProvider.createInstance` returns a new instance, and `addUI` closes the previous one.

## Guarding `doInit` does not help

```
mvn spring-boot:run -Dspring-boot.run.jvmArguments=-Drepro.guard=true
```

`ReproUI.doInit` then returns early when the UI is already initialized. The exception is gone, but
`addUI` still closes the UI and step 4 still reloads the page:

```
WARN  repro.ReproUI : REPRO guard: UI 1 is already initialized, ignoring doInit for UI id 2
WARN  repro.ReproUI : REPRO: legacy UI 1 is being closed by com.vaadin.server.VaadinSession.addUI(VaadinSession.java:1237) <- com.vaadin.server.communication.UIInitHandler.getBrowserDetailsUI(UIInitHandler.java:232)
```

## Variant: the browser only sees the second response

```
mvn spring-boot:run -Dspring-boot.run.jvmArguments=-Drepro.resendFirstInit=true
```

`ResendFirstInitRequestFilter` lets the server handle every legacy init request twice and discards
the first response. This simulates a request whose response is lost and that is sent again by the
browser or by a proxy. Open the page: the exception is logged during the page load, the page looks
normal, and the first click on "Flow round trip" reloads it.

## Application-side workarounds

Both were tested with the button and with `-Drepro.resendFirstInit=true`. In all four runs there
was no exception, no `close()`, no page reload, and the Flow and the legacy button kept working.

```
mvn spring-boot:run -Dspring-boot.run.jvmArguments=-Drepro.workaround=embedId
mvn spring-boot:run -Dspring-boot.run.jvmArguments=-Drepro.workaround=provider
```

- `embedId`: the guard in `doInit`, plus `getEmbedId()` returns `null`. Without an embed id
  `VaadinSession.addUI` has no previous UI to close.
- `provider`: `RefreshingMprUIProvider` overrides `isPreservedOnRefresh` to return `true`.
  `UIInitHandler` then finds the existing UI by its embed id and calls `doRefresh` instead of
  `doInit` and `addUI`. The embed id contains the MPR content id, which is unique per Flow UI, so
  a normal page reload still creates a new legacy UI.

Both need one more step. The response to the second init request takes a legacy sync id. A client
that has already processed the first response (button scenario) never sees that id:

```
Received message with server id 6 but expected 5. Postponing handling until the missing message(s) have been received
Gave up waiting for message 5 from the server
Resynchronize from server requested
```

After that the legacy client sent no further requests in our runs, so the legacy part of the page
stayed frozen. `ReproUI.repaintAfterResponse` avoids this: it calls `repaintAll` in an `access`
task, which runs after the response has been written. The next legacy response is then marked as
a resynchronization and the client accepts it. `-Drepro.repaint=false` switches this off.

## `closeIdleSessions`

[#54](https://github.com/vaadin/multiplatform-runtime/issues/54) mentions that the exception only
appeared with `closeIdleSessions=true`. Two sequences were tested with MPR 8.0.1; neither produced
the exception:

```
mvn spring-boot:run -Dspring-boot.run.arguments="--vaadin.closeIdleSessions=true --server.servlet.session.timeout=60s --vaadin.heartbeatInterval=20"
```

- Leave the page idle. About 80 seconds after the last UIDL request a heartbeat closes the Flow
  session and both UIs. The next heartbeat gets a 403 and the client reloads the page. The new
  Flow session and the new legacy session both start with UI id 0 and a new legacy UI instance.
- With `--vaadin.heartbeatInterval=50` and `-Drepro.push=true`: reload the page 75 seconds after
  the last UIDL request, before a heartbeat has closed the expired session. The first request of
  the reload closes the old session, the init request creates a new one.

## How this happens in production

In our application the exception appears a few times per year with consecutive UI ids (for example
"as UI id 5 ... again as UI id 6"). We have not been able to verify why the request arrives twice.
`AbstractLegacyWrapper.initContent` triggers it only once per Flow UI, and `vaadinBootstrap.js`
does not repeat it, so the most likely cause is a request that is sent again below the application
(browser or proxy after a lost response).
