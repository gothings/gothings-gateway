package br.ufs.gothings.gateway;

import br.ufs.gothings.core.GwHeaders;
import br.ufs.gothings.core.message.GwReply;
import br.ufs.gothings.core.message.GwRequest;
import br.ufs.gothings.core.message.headers.Operation;
import br.ufs.gothings.gateway.block.Block;
import br.ufs.gothings.gateway.block.BlockId;
import br.ufs.gothings.gateway.block.Forwarding;
import br.ufs.gothings.gateway.exceptions.InvalidForwardingException;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * @author Wagner Macedo
 */
public class InterconnectionController implements Block {
    private final CommunicationManager manager;

    public InterconnectionController(final CommunicationManager manager) {
        this.manager = manager;
    }

    @Override
    public void receiveForwarding(final BlockId sourceId, final Forwarding fwd) throws InvalidForwardingException {
        switch (sourceId) {
            case INPUT_CONTROLLER:
                final GwRequest request = (GwRequest) fwd.getMessage();
                final GwHeaders headers = request.headers();

                final URI uri = createURI(headers.get(GwHeaders.PATH));
                if (uri == null) {
                    return;
                }

                final String protocol = uri.getScheme();
                final String target = uri.getRawAuthority();
                headers.set(GwHeaders.TARGET, target);

                final String targetAndPath = uri.getRawSchemeSpecificPart();
                final String path = StringUtils.replaceOnce(targetAndPath, target, "");
                headers.set(GwHeaders.PATH, path);

                final GwReply cached = getCache(request, protocol);
                if (cached != null) {
                    manager.forward(this, BlockId.OUTPUT_CONTROLLER, new Forwarding(cached, protocol));
                } else {
                    manager.forward(this, BlockId.COMMUNICATION_MANAGER, new Forwarding(request, protocol));
                }
                break;
            case COMMUNICATION_MANAGER:
                final GwReply reply = (GwReply) fwd.getMessage();
                setCache(reply, (String) fwd.getExtraInfo());
                manager.forward(this, BlockId.OUTPUT_CONTROLLER, new Forwarding(reply, fwd.getExtraInfo()));
                break;
        }
    }

    private void setCache(final GwReply reply, final String protocol) {

    }

    private GwReply getCache(final GwRequest req, final String protocol) {
        if (req.headers().get(GwHeaders.OPERATION) != Operation.READ) {
            return null;
        }

        // TODO: Method stub
        return null;
    }

    private URI createURI(final String path) {
        final String s_uri = path.replaceFirst("^/+", "").replaceFirst("/+", "://");
        try {
            final URIBuilder uri = new URIBuilder(s_uri);

            // sort query parameters
            final List<NameValuePair> params = uri.getQueryParams();
            params.sort((o1, o2) -> o1.getName().compareTo(o2.getName()));
            uri.setParameters(params);

            return uri.build();
        }
        catch (URISyntaxException e) {
            return null;
        }
    }
}
