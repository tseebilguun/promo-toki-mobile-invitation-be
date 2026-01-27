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
            "/getgeneralinfo",
            "/login"
    );

    @Inject
    JwtService jwtService;

    @Override
    public void filter(ContainerRequestContext ctx) {
        String rawPath = ctx.getUriInfo() != null ? ctx.getUriInfo().getPath() : null;
        String path = canonicalPath(rawPath);

        logger.info("========== JWT Filter Debug ==========");
        logger.info("Raw path: " + rawPath);
        logger.info("Canonical path: " + path);

        if (PUBLIC_PATHS.contains(path)) {
            logger.info("Public endpoint: " + path + " - ALLOWED");
            return;
        }

        logger.info("Protected endpoint: " + path + " - checking auth...");

        String authHeader = ctx.getHeaderString("Authorization");
        logger.info("Authorization header: " + (authHeader != null ? authHeader.substring(0, Math.min(30, authHeader.length())) + "..." : "NULL"));

        if (authHeader == null || !authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            logger.warn("Missing or invalid Authorization header");
            abort(ctx, "Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) {
            logger.warn("Empty token");
            abort(ctx, "Empty token");
            return;
        }

        logger.info("Token extracted (first 20 chars): " + token.substring(0, Math.min(20, token.length())) + "...");

        boolean isExpired = jwtService.isExpiredOrInvalid(token);
        logger.info("Token expired/invalid check: " + isExpired);

        if (isExpired) {
            logger.warn("Token expired or invalid - REJECTING");
            abort(ctx, "Token expired or invalid");
            return;
        }

        String subject = jwtService.extractSubject(token);
        String tokiId = jwtService.getStringClaim(token, "tokiId");
        String msisdn = jwtService.getStringClaim(token, "msisdn");

        logger.info("Token claims extracted:");
        logger.info("  - subject: " + subject);
        logger.info("  - tokiId: " + tokiId);
        logger.info("  - msisdn: " + msisdn);

        ctx.setProperty("jwt.tokiId", tokiId);
        ctx.setProperty("jwt.msisdn", msisdn);
        ctx.setProperty("jwt.subject", subject);

        logger.info("JWT validation SUCCESS - request allowed");
        logger.info("======================================");
    }

    private static String canonicalPath(String rawPath) {
        if (rawPath == null) {
            return "/";
        }
        String p = rawPath.trim();
        if (p.isEmpty()) {
            return "/";
        }
        // UriInfo#getPath() is commonly returned without leading '/', but can vary.
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        // normalize: collapse trailing slashes
        while (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p.toLowerCase();
    }

    private void abort(ContainerRequestContext ctx, String message) {
        logger.warn("ABORTING REQUEST: " + message);
        ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .entity(new CustomResponse<>("fail", message, null))
                .build());
    }
}