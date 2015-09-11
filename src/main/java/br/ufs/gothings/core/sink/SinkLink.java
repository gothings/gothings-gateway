package br.ufs.gothings.core.sink;

import br.ufs.gothings.core.GwMessage;

/**
 * @author Wagner Macedo
 */
public interface SinkLink {
    void send(GwMessage value);

    void setListener(SinkListener listener);
}
