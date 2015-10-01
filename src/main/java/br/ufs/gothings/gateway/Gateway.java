package br.ufs.gothings.gateway;

import br.ufs.gothings.core.plugin.GwPlugin;
import br.ufs.gothings.core.Settings;
import br.ufs.gothings.core.Settings.Key;
import br.ufs.gothings.core.plugin.PluginClient;
import br.ufs.gothings.core.plugin.PluginServer;
import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;
import org.apache.commons.cli.*;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

/**
 * @author Wagner Macedo
 */
public final class Gateway {
    public static void main(String[] args) throws ParseException, FileNotFoundException, GatewayConfigException, ClassNotFoundException, IllegalAccessException, InstantiationException {
        final GatewayParser parser = new GatewayParser(args);
        final GatewayConfig cfg = parser.getConfig();

        final CommunicationManager manager = new CommunicationManager();

        for (final PluginConfig p : cfg.plugins) {
            registerPlugin(manager, p);
        }

        manager.start();
    }

    private static final Pattern RE_TYPE = Pattern.compile("(?i)client|server|client\\+server|server\\+client");

    private static void registerPlugin(final CommunicationManager manager, final PluginConfig p) throws InstantiationException, IllegalAccessException, ClassNotFoundException, GatewayConfigException {
        final GwPlugin plugin = Class.forName(p.className).asSubclass(GwPlugin.class).newInstance();

        final String protocol = p.protocol;
        if (!protocol.equals(plugin.getProtocol())) {
            throw new GatewayConfigException("the class %s does not implement %s protocol", p.className, protocol);
        }

        final String type = p.type;
        if (RE_TYPE.matcher(type).matches()) {
            if (type.contains("client") && !(plugin instanceof PluginClient)) {
                throw new GatewayConfigException("the class %s is declared as client but don't implement PluginClient", p.className);
            }
            if (type.contains("server") && !(plugin instanceof PluginServer)) {
                throw new GatewayConfigException("the class %s is declared as server but don't implement PluginServer", p.className);
            }
        } else {
            throw new GatewayConfigException("%s plugin type misinformed", protocol);
        }

        final Settings settings = plugin.settings();
        for (final Entry<String, String> entry : p.properties.entrySet()) {
            final String name = entry.getKey();
            final Key<?> key = settings.getKey(name);
            if (key == null) {
                throw new GatewayConfigException("property '%s' not registered for %s plugin", name, protocol);
            }
            settings.put(name, convert(entry.getValue(), key));
        }

        if (plugin instanceof PluginClient) {
            manager.register((PluginClient) plugin);
        }
        if (plugin instanceof PluginServer) {
            manager.register((PluginServer) plugin);
        }
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

            final YamlReader yaml = new YamlReader(new FileReader(configFileName));
            yaml.getConfig().setPropertyElementType(GatewayConfig.class, "plugins", PluginConfig.class);

            try {
                config = yaml.read(GatewayConfig.class);
            } catch (YamlException e) {
                throw new GatewayConfigException();
            }
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

    protected static class GatewayConfig {
        public List<PluginConfig> plugins;

        @Override
        public String toString() {
            return String.format("{plugins=%s}", plugins);
        }
    }

    protected static class PluginConfig {
        public String protocol;
        public String className;
        public String type;
        public Map<String, String> properties = Collections.emptyMap();

        @Override
        public String toString() {
            return String.format("(%s, %s, %s)", protocol, className, properties);
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
