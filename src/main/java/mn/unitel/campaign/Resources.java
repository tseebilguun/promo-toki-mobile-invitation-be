package mn.unitel.campaign;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import mn.unitel.campaign.models.InvitationReq;
import mn.unitel.campaign.models.LoginReq;

@Path("/")
@Consumes("application/json")
@Produces("application/json")
public class Resources {
    @Inject
    MainService mainService;

    @POST
    @Path("/login")
    @Produces(MediaType.APPLICATION_JSON)
    public Response login(LoginReq loginRequest) {
        return mainService.login(loginRequest);
    }

    @GET
    @Path("/getInfo")
    public Response getInfo(@Context ContainerRequestContext ctx) {
        return mainService.getInfoByJwt(ctx);
    }

    // For DSD
    @GET
    @Path("/getGeneralInfo")
    public Response getInfo(@QueryParam("msisdn") String msisdn, @QueryParam("tokiId") String tokiId) {
        return mainService.getInfo(msisdn, tokiId);
    }

    @POST
    @Path("/sendInvite")
    public Response sendInvite(InvitationReq req, @Context ContainerRequestContext ctx){
        return mainService.sendInvite(req, ctx);
    }
}
