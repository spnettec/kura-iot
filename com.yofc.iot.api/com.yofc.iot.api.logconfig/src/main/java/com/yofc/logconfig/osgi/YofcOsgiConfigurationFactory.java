package com.yofc.logconfig.osgi;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Order;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.xml.XmlConfiguration;

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
            Appender appender = config.getAppender("YofcOsgiAppender");
            System.out.println(appender);

        }
    }
}
