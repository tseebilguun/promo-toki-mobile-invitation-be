package mn.unitel.campaign.filters;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class OutgoingRequestLoggingFilter implements ClientRequestFilter {
    private static final Logger logger = Logger.getLogger(OutgoingRequestLoggingFilter.class);

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule().addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        logger.info("----------------- Outgoing Request -----------------");
        logger.infof("%s %s", requestContext.getMethod(), requestContext.getUri().toString());
        requestContext.getHeaders().forEach((key, value) -> logger.infof("Header: %s: %s", key, value));

        if (requestContext.hasEntity()) {
            Object entity = requestContext.getEntity();
            String responseBody = serializeToJson(entity);
            if (responseBody.length() < 10000)
                logger.infof("Request Body: %s", responseBody);
        }
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
}
