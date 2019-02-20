package com.yofc.log.osgi;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Order;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.xml.XmlConfiguration;
import org.apache.logging.log4j.core.layout.PatternLayout;

@Plugin(name = "YofcOsgiConfigurationFactory", category = ConfigurationFactory.CATEGORY)
@Order(50)
public class YofcOsgiConfigurationFactory extends ConfigurationFactory {

    /**
     * Valid file extensions for XML files.
     */
    public static final String[] SUFFIXES = new String[] { ".xml", "*" };

    /**
     * Returns the file suffixes for XML files.
     * 
     * @return An array of File extensions.
     */
    public String[] getSupportedTypes() {
        return SUFFIXES;
    }

    @Override
    public Configuration getConfiguration(LoggerContext loggerContext, ConfigurationSource source) {
        return new YofcOsgiXMLConfiguration(loggerContext, source);
    }

    private class YofcOsgiXMLConfiguration extends XmlConfiguration {

        public YofcOsgiXMLConfiguration(LoggerContext source, ConfigurationSource configSource) {
            super(source, configSource);
        }

        @Override
        protected void doConfigure() {
            super.doConfigure();
            final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            final Configuration config = ctx.getConfiguration();
            final Layout<?> layout = PatternLayout.newBuilder()
                    .withPattern("%d{ISO8601} [%t] %-5p %c{1.} - %m%n%throwable{full}").build();
            final Appender appender = YofcOsgiAppender.createAppender("YofcOsgiAppender", null, layout, "false");
            appender.start();
            addAppender(appender);
            AppenderRef ref = AppenderRef.createAppenderRef("YofcOsgiAppender", Level.INFO, null);
            AppenderRef[] refs = new AppenderRef[] { ref };
            LoggerConfig loggerConfig = LoggerConfig.createLogger(true, Level.ERROR, "org.eclipse", "true", refs, null,
                    config, null);
            loggerConfig.addAppender(appender, null, null);
            addLogger("org.eclipse", loggerConfig);

        }
    }
}
