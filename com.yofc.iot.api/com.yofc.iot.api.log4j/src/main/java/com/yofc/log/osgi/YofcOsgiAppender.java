package com.yofc.log.osgi;

import java.io.Serializable;
import java.util.Map;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.util.Booleans;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

@Plugin(name = "YofcOsgi", category = "Core", elementType = "appender", printObject = true)
public class YofcOsgiAppender extends AbstractAppender {

    protected YofcOsgiAppender(final String name, final Filter filter, final Layout<? extends Serializable> layout,
            final boolean ignoreExceptions) {
        super(name, filter, layout, ignoreExceptions);
    }

    @PluginFactory
    public static YofcOsgiAppender createAppender(@PluginAttribute("name") final String name,
            @PluginElement("Filters") final Filter filter,
            @PluginElement("Layout") Layout<? extends Serializable> layout,
            @PluginAttribute("ignoreExceptions") final String ignore) {

        if (name == null) {
            LOGGER.error("No name provided for YofcOsgiAppender");
            return null;
        } else {
            if (layout == null) {
                layout = PatternLayout.createDefaultLayout();
            }
            final boolean ignoreExceptions = Booleans.parseBoolean(ignore, true);
            return new YofcOsgiAppender(name, filter, layout, ignoreExceptions);
        }
    }

    @Override
    public void append(LogEvent arg0) {
        this.getLayout().toByteArray(arg0);
    }

    @Override
    public void start() {
        BundleContext bundleContext = null;

        Bundle bundle = FrameworkUtil.getBundle(getClass());
        if (bundle != null) {
            bundleContext = bundle.getBundleContext();
        }
        if (bundleContext == null) {
            throw new IllegalArgumentException("missing BundleContext");
        }

        Map<String, Appender> apps = LoggerContext.getContext().getConfiguration().getAppenders();
        super.start();
    }

    @Override
    public void stop() {
        super.stop();

    }

}
