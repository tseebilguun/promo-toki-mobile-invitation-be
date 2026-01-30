package clients.dsd_client;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import mn.unitel.campaign.filters.OutgoingRequestLoggingFilter;
import mn.unitel.campaign.filters.OutgoingResponseLoggingFilter;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/third-party")
@RegisterRestClient(configKey = "dsd.client")
@ClientHeaderParam(name = "Accept", value = "*/*")
@RegisterProvider(OutgoingRequestLoggingFilter.class)
@RegisterProvider(OutgoingResponseLoggingFilter.class)
@ClientHeaderParam(name = "Content-Type", value = MediaType.APPLICATION_JSON)
@ClientHeaderParam(name = "Authorization", value = "Basic Y2FtcGFpZ246d2RTR3lAOHREQERBM100Vg==")
public interface DsdClient {
    @GET
    @Path("/user-id")
    @Produces(MediaType.APPLICATION_JSON)
    NumberRelationRes getUserId(@QueryParam("msisdn") String msisdn);
}
