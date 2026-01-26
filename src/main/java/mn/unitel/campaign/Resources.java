package mn.unitel.campaign;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;

@Path("/")
@Consumes("application/json")
@Produces("application/json")
public class Resources {
    @Inject
    Services services;

    @POST
    @Path("/getInfo")
    public Response getInfo(@QueryParam("tokiId") String tokiId) {
        return services.getInfo(tokiId);
    }
}
