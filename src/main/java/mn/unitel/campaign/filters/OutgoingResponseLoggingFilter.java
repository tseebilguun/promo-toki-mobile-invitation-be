package mn.unitel.campaign.filters;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientResponseContext;
import jakarta.ws.rs.client.ClientResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Provider
public class OutgoingResponseLoggingFilter implements ClientResponseFilter {
    private static final Logger logger = Logger.getLogger(OutgoingResponseLoggingFilter.class);

    @Override
    public void filter(ClientRequestContext clientRequestContext, ClientResponseContext clientResponseContext) throws IOException {
        logger.info("----------------- Outgoing Request's Response -----------------");
        logger.infof("Status: %d", clientResponseContext.getStatus());
        if (clientResponseContext.hasEntity()) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            clientResponseContext.getEntityStream().transferTo(buffer);
            String responseBody = new BufferedReader(
                    new InputStreamReader(new ByteArrayInputStream(buffer.toByteArray()), StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));
            if (responseBody.length() < 10000)
                logger.infof("Response Body: %s", responseBody);
            clientResponseContext.setEntityStream(new ByteArrayInputStream(buffer.toByteArray()));
        }
    }
}