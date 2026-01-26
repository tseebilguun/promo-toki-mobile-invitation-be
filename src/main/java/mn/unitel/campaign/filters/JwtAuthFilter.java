package mn.unitel.campaign.filters;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import mn.unitel.campaign.JwtService;
import mn.unitel.campaign.models.CustomResponse;
import org.jboss.logging.Logger;

import java.util.Set;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class JwtAuthFilter implements ContainerRequestFilter {

    private static final Logger logger = Logger.getLogger(JwtAuthFilter.class);

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/getGeneralInfo",
            "/login"
    );

    @Inject
    JwtService jwtService;

    @Override
    public void filter(ContainerRequestContext ctx) {
        String rawPath = ctx.getUriInfo().getPath();
        String path = rawPath == null ? "" : rawPath.trim().toLowerCase();

        if (PUBLIC_PATHS.contains(path)) {
            logger.debug("Public endpoint: " + path);
            return;
        }

        String firstSegment = extractFirstSegment(path);

        String authHeader = ctx.getHeaderString("Authorization");
        if (authHeader == null || !authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            abort(ctx, "Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) {
            abort(ctx, "Empty token");
            return;
        }

        if (jwtService.isExpiredOrInvalid(token)) {
            abort(ctx, "Token expired or invalid");
            return;
        }

        String subject = jwtService.extractSubject(token);
        String nationalId = jwtService.getStringClaim(token, "nationalId");
        String tokiId = jwtService.getStringClaim(token, "tokiId");
        String phone = jwtService.getStringClaim(token, "phone");
        String accountName = jwtService.getStringClaim(token, "accountName");
        if (nationalId == null) {
            abort(ctx, "Missing nationalId claim");
            return;
        }

        ctx.setProperty("jwt.tokiId", tokiId);
        ctx.setProperty("jwt.phone", phone);
        ctx.setProperty("jwt.accountName", accountName);
        ctx.setProperty("jwt.nationalId", nationalId);
        ctx.setProperty("jwt.subject", subject);
    }

    private String extractFirstSegment(String path) {
        if (path.isEmpty()) return "";
        int slash = path.indexOf('/');
        return (slash == -1 ? path : path.substring(0, slash));
    }

    private void abort(ContainerRequestContext ctx, String message) {
        logger.debug("Aborting request: " + message);
        ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .entity(new CustomResponse<>("fail", message, null))
                .build());
    }
}