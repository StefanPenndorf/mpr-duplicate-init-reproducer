package repro;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.mpr.core.LegacyUI;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@LegacyUI(ReproUI.class)
@SpringBootApplication
public class Application implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
