package com.yofc.logconfig.osgi;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.*;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.xml.XmlConfiguration;

@Plugin(name = "YofcOsgiConfigurationFactory", category = ConfigurationFactory.CATEGORY)
@Order(50)
public class YofcOsgiConfigurationFactory extends ConfigurationFactory {

  /** Valid file extensions for XML files. */
  public static final String[] SUFFIXES = new String[] {".xml", "*"};

  /**
   * Returns the file suffixes for XML files.
   *
   * @return An array of File extensions.
   */
  @Override
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
      ConfigurationBuilder<BuiltConfiguration> builder = newConfigurationBuilder();
      AppenderComponentBuilder appenderBuilder =
          builder.newAppender("YofcOsgiAppender", "YofcOsgiAppender");
      appenderBuilder.add(
          builder
              .newLayout("PatternLayout")
              .addAttribute("pattern", "%d [%t] %-5level: %msg%n%throwable"));
      builder.add(appenderBuilder);

      Appender demoAppender = builder.build().getAppender("YofcOsgiAppender");

      demoAppender.start();
      config.addAppender(demoAppender);
      AppenderRef ref = AppenderRef.createAppenderRef("YofcOsgiAppender", null, null);
      AppenderRef[] refs = new AppenderRef[] {ref};
      LoggerConfig loggerConfig =
          LoggerConfig.createLogger(
              false, Level.DEBUG, "org.eclipse", "true", refs, null, this, null);
      config.addLogger("org.eclipse", loggerConfig);
    }
  }
}
