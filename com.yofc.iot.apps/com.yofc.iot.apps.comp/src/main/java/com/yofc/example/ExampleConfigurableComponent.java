package com.yofc.example;

import java.util.Map;
import java.util.TreeMap;

import org.eclipse.kura.configuration.ConfigurableComponent;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.Designate;

@Designate(ocd = Config.class)
@Component(immediate = true, configurationPolicy = ConfigurationPolicy.REQUIRE)
public class ExampleConfigurableComponent implements ConfigurableComponent {

    @Activate
    public void activate(Map<String, Object> properties) {
        dumpProperties("activate", properties);
    }

    @Modified
    public void modified(Map<String, Object> properties) {
        dumpProperties("modified", properties);
    }

    @Deactivate
    public void deactivate() {
        System.out.println("deactivate");
    }

    private void dumpProperties(String operation, final Map<String, Object> properties) {
        final Map<String, Object> sortedMap = new TreeMap<>(properties);

        System.out.format("=========== %s ===========%n", operation);
        for (Map.Entry<String, Object> entry : sortedMap.entrySet()) {
            System.out.format("\t'%s' = '%s'%n", entry.getKey(), entry.getValue());
        }
        System.out.format("=========== %s ===========%n", operation);
    }
}
