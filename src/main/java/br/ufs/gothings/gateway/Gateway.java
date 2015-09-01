package br.ufs.gothings.gateway;

import br.ufs.gothings.plugins.http.HttpPlugin;
import br.ufs.gothings.plugins.mqtt.MqttPlugin;

/**
 * @author Wagner Macedo
 */
public final class Gateway {
    public static void main(String[] args) {
        final CommunicationManager manager = new CommunicationManager();

        final HttpPlugin httpPlugin = new HttpPlugin();
        manager.register(httpPlugin);

        final MqttPlugin mqttPlugin = new MqttPlugin();
        manager.register(mqttPlugin);

        manager.start();
    }
}
