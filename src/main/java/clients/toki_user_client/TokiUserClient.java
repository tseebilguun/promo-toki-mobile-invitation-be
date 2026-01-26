package clients.toki_user_client;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import mn.unitel.campaign.filters.OutgoingRequestLoggingFilter;
import mn.unitel.campaign.filters.OutgoingResponseLoggingFilter;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/jump/v1")
@RegisterRestClient(configKey="toki.user.client")
@RegisterProvider(OutgoingRequestLoggingFilter.class)
@RegisterProvider(OutgoingResponseLoggingFilter.class)
@ClientHeaderParam(name = "im_api_key", value = "unitel123")
public interface TokiUserClient {
    @GET
    @Path("/user/user-details")
    @Consumes("application/json")
    TokiUserClientRes getUserDetails(@QueryParam("pNumber") String phoneNo);
}
