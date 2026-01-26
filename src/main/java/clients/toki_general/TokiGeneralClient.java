package clients.toki_general;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import mn.unitel.campaign.filters.OutgoingRequestLoggingFilter;
import mn.unitel.campaign.filters.OutgoingResponseLoggingFilter;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/third-party-service/v1")
@RegisterRestClient(configKey = "toki-general")
@ClientHeaderParam(name = "Accept", value = "*/*")
@RegisterProvider(OutgoingRequestLoggingFilter.class)
@RegisterProvider(OutgoingResponseLoggingFilter.class)
@ClientHeaderParam(name = "Content-Type", value = MediaType.APPLICATION_JSON)
public interface TokiGeneralClient {
    @Path("/notification/single-push")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    TokiNotiGetTokenRes send(
            @HeaderParam("Authorization") String authorization,
            TokiNotiReq tokiNotiReq
    );

    @GET
    @Path("/auth/token")
    @ClientHeaderParam(name = "Authorization", value = "Basic T1I5UEFnRXNYcjpSZ2NxNll6QXJTR0YxU081MW82ZWFxekFOOVI2UjlJd3RublNMQjlJ")
    @Produces(MediaType.APPLICATION_JSON)
    TokiNotiGetTokenRes getToken();
}
