package br.ufs.gothings.gateway;

import br.ufs.gothings.core.plugin.GwPlugin;
import br.ufs.gothings.core.Settings;
import br.ufs.gothings.core.Settings.Key;
import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;
import org.apache.commons.cli.*;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * @author Wagner Macedo
 */
public final class Gateway {
    public static void main(String[] args) throws ParseException, FileNotFoundException, GatewayConfigException, ClassNotFoundException, IllegalAccessException, InstantiationException {
        final GatewayParser parser = new GatewayParser(args);
        final GatewayConfig config = parser.getConfig();

        final CommunicationManager manager = new CommunicationManager();

        for (final PluginConfig p : config.getConfiguredPlugins()) {
            final String protocol = p.getProtocol();
            final GwPlugin plugin = Class.forName(p.getClassName()).asSubclass(GwPlugin.class).newInstance();
            if (!protocol.equals(plugin.getProtocol())) {
                throw new GatewayConfigException("the class %s does not implement %s protocol", p.getClassName(), protocol);
            }

            final Settings settings = plugin.settings();
            for (final Entry<String, String> entry : p.getProperties().entrySet()) {
                final String name = entry.getKey();
                final Key<?> key = settings.getKey(name);
                if (key == null) {
                    throw new GatewayConfigException("property '%s' not registered for %s protocol", name, protocol);
                }
                settings.put(name, convert(entry.getValue(), key));
            }

            manager.register(plugin);
        }

        manager.start();
    }

    @SuppressWarnings("unchecked")
    private static <T> T convert(final String value, final Key<?> key) {
        final Class<?> cls = key.getClassType();
        if (cls == String.class)
            return (T) value;
        if (cls == Integer.class)
            return (T) Integer.valueOf(value);
        if (cls == Long.class)
            return (T) Long.valueOf(value);
        if (cls == Float.class)
            return (T) Float.valueOf(value);
        if (cls == Double.class)
            return (T) Double.valueOf(value);
        if (cls == Short.class)
            return (T) Short.valueOf(value);
        if (cls == Byte.class)
            return (T) Byte.valueOf(value);

        return null;
    }

    private static class GatewayParser {
        private GatewayConfig config;

        public GatewayParser(final String[] args) throws ParseException, FileNotFoundException, GatewayConfigException {
            final Options opts = getOptions();
            final CommandLineParser parser = new DefaultParser();
            final CommandLine cmd = parser.parse(opts, args);

            String configFileName = cmd.getOptionValue("config");
            if (configFileName == null) {
                configFileName = ClassLoader.getSystemResource("default-config.yml").getFile();
            }
            config = new GatewayConfig(configFileName);
        }

        private Options getOptions() {
            final Options opts = new Options();
            opts.addOption(Option.builder("config")
                    .argName("FILE")
                    .hasArg()
                    .desc("Config file with the settings")
                    .build());
            return opts;
        }

        public GatewayConfig getConfig() {
            return config;
        }
    }

    private static class GatewayConfig {
        private final List<PluginConfig> configuredPlugins = new ArrayList<>();

        @SuppressWarnings("unchecked")
        public GatewayConfig(final String fileName) throws FileNotFoundException, GatewayConfigException {
            final YamlReader yaml = new YamlReader(new FileReader(fileName));
            try {
                parseConfig((Map<String, Object>) yaml.read());
            } catch (YamlException e) {
                throw new GatewayConfigException();
            }
        }

        @SuppressWarnings("unchecked")
        private void parseConfig(final Map<String, Object> cfg) throws GatewayConfigException {
            for (final Entry<String, Object> cfgEntry : cfg.entrySet()) {
                switch (cfgEntry.getKey()) {
                    case "plugins":
                        final List<Map<String, Object>> plugins = (List<Map<String, Object>>) cfgEntry.getValue();
                        parsePluginConfig(plugins);
                        break;
                    default:
                        throw new GatewayConfigException();
                }
            }
        }

        private void parsePluginConfig(final List<Map<String, Object>> plugins) throws GatewayConfigException {
            for (final Map<String, Object> plugin : plugins) {
                if (!(plugin.containsKey("protocol") && plugin.containsKey("class"))) {
                    throw new GatewayConfigException();
                }

                final PluginConfig pluginConfig = new PluginConfig();
                for (final Entry<String, Object> entry : plugin.entrySet()) {
                    final String key = entry.getKey();
                    final Object value = entry.getValue();
                    switch (key) {
                        case "protocol":
                            pluginConfig.setProtocol((String) value);
                            break;
                        case "class":
                            pluginConfig.setClassName((String) value);
                            break;
                        default:
                            pluginConfig.getProperties().put(key, (String) value);
                    }
                }
                configuredPlugins.add(pluginConfig);
            }
        }

        public List<PluginConfig> getConfiguredPlugins() {
            return configuredPlugins;
        }
    }

    private static class PluginConfig {
        private String protocol;
        private String className;
        private final Map<String, String> properties = new HashMap<>();

        public String getProtocol() {
            return protocol;
        }

        public void setProtocol(final String protocol) {
            this.protocol = protocol;
        }

        public String getClassName() {
            return className;
        }

        public void setClassName(final String className) {
            this.className = className;
        }

        public Map<String, String> getProperties() {
            return properties;
        }
    }

    private static class GatewayConfigException extends Exception {
        public GatewayConfigException() {
            super("malformed configuration file");
        }

        public GatewayConfigException(final String msg, Object... args) {
            super(String.format(msg, args));
        }
    }
}
