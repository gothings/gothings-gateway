package br.ufs.gothings.core;

import java.util.concurrent.Future;

/**
 * @author Wagner Macedo
 */
public interface CommunicationManager {
    Future<GwMessage> sendRequest(GwMessage message);
}
