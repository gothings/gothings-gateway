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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * @author Wagner Macedo
 */
public final class Gateway {
    private static final Predicate<String> validType = Pattern.compile("(?i)client|server|client\\+server|server\\+client").asPredicate();

    public static void main(String[] args) throws ParseException, FileNotFoundException, GatewayConfigException, ClassNotFoundException, IllegalAccessException, InstantiationException {
        final GatewayParser parser = new GatewayParser(args);
        final GatewayConfig cfg = parser.getConfig();

        final CommunicationManager manager = new CommunicationManager();

        final Map<String, PluginBundle> map = new HashMap<>();
        for (final PluginConfig p : cfg.plugins) {
            final PluginBundle bundle = map.computeIfAbsent(p.protocol, PluginBundle::new);
            if (validType.test(p.type)) {
                final Class<?> cls = Class.forName(p.className);
                if (p.type.contains("client")) {
                    bundle.setClient(cls, p);
                }
                if (p.type.contains("server")) {
                    bundle.setServer(cls, p);
                }
            } else {
                throw new GatewayConfigException("%s plugin type misinformed", p.protocol);
            }
        }

        for (final PluginBundle bundle : map.values()) {
            registerPlugin(manager, bundle);
        }
        manager.start();
    }

    private static final class PluginBundle {
        private final String protocol;

        private PluginConfig client;
        private PluginConfig server;

        private Class<? extends PluginClient> clientClass;
        private Class<? extends PluginServer> serverClass;


        public PluginBundle(final String protocol) {
            this.protocol = protocol;
        }

        public void setClient(final Class<?> cls, final PluginConfig client) throws GatewayConfigException {
            if (clientClass != null) {
                throw new GatewayConfigException("%s client plugin already set", protocol);
            }
            clientClass = cls.asSubclass(PluginClient.class);
            this.client = client;
        }

        public void setServer(final Class<?> cls, final PluginConfig server) throws GatewayConfigException {
            if (serverClass != null) {
                throw new GatewayConfigException("%s server plugin already set", protocol);
            }
            serverClass = cls.asSubclass(PluginServer.class);
            this.server = server;
        }
    }

    private static void registerPlugin(final CommunicationManager manager, final PluginBundle p) throws IllegalAccessException, InstantiationException, GatewayConfigException {
        if (p.clientClass == p.serverClass) {
            final GwPlugin plugin = buildPlugin(p.client, p.clientClass);
            manager.register((PluginClient) plugin, (PluginServer) plugin);
        }

        else if (p.clientClass != null) {
            final PluginClient pluginClient = buildPlugin(p.client, p.clientClass);
            manager.register(pluginClient);
        }

        else {
            final PluginServer pluginServer = buildPlugin(p.server, p.serverClass);
            manager.register(pluginServer);
        }
    }

    private static <T extends GwPlugin> T buildPlugin(final PluginConfig cfg, final Class<T> pluginClass) throws GatewayConfigException, IllegalAccessException, InstantiationException {
        final T plugin = pluginClass.newInstance();

        if (!cfg.protocol.equals(plugin.getProtocol())) {
            throw new GatewayConfigException("the class %s does not implement %s protocol", plugin.getClass().getName(), cfg.protocol);
        }

        final Settings settings = plugin.settings();
        for (final Entry<String, String> entry : cfg.properties.entrySet()) {
            final String name = entry.getKey();
            final Key<?> key = settings.getKey(name);
            if (key == null) {
                throw new GatewayConfigException("property '%s' not registered for %s plugin", name, cfg.protocol);
            }
            settings.put(name, convert(entry.getValue(), key));
        }

        return plugin;
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
