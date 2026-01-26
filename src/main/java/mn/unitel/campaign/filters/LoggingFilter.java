package mn.unitel.campaign.filters;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Provider
public class LoggingFilter implements ContainerRequestFilter, ContainerResponseFilter {
    private static final Logger logger = Logger.getLogger(LoggingFilter.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule().addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        logger.info("***************** Incoming Request *****************");
        logger.infof("%s %s", requestContext.getMethod(), requestContext.getUriInfo().getRequestUri().toString());

        if (requestContext.hasEntity()) {
            InputStream originalInputStream = requestContext.getEntityStream();
            String body = inputStreamToString(originalInputStream);

            // ALWAYS reset the stream first - this is critical!
            requestContext.setEntityStream(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));

            // Then log if not blank
            if (!body.isBlank()) {
                String prettyBody = formatJson(body);
                if (prettyBody.length() < 10000) {
                    logger.infof("Incoming request Body: %s", prettyBody);
                }
            }
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        logger.info("***************** Incoming Request's Response *****************");
        logger.info("Status: " + responseContext.getStatus());
        if (responseContext.hasEntity()) {
            Object entity = responseContext.getEntity();
            String responseBody = serializeToJson(entity);
            logger.infof("Body: %s", responseBody);
        }
    }

    private String inputStreamToString(InputStream inputStream) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toString(StandardCharsets.UTF_8.name());
    }

    private String serializeToJson(Object entity) {
        try {
            // Serialize the entity to a beautified JSON string
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(entity);
        } catch (IOException e) {
            logger.error("Failed to serialize response body to JSON", e);
            return "Error serializing response body";
        }
    }

    private String formatJson(String json) {
        try {
            Object jsonObject = objectMapper.readValue(json, Object.class);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject);
        } catch (IOException e) {
            logger.error("Failed to format JSON", e);
            return json;
        }
    }
}