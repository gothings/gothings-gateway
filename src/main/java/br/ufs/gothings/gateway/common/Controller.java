package br.ufs.gothings.gateway.common;

/**
 * @author Wagner Macedo
 */
public interface Controller {
    void process(Package pkg) throws Exception;
}
