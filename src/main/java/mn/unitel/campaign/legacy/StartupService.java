package mn.unitel.campaign.legacy;

import Executable.APIUtil;
import Utils.CustomLoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.Getter;
import mn.unitel.campaign.Utils;
import mn.unitel.campaign.database.DBPoolFactory;
import mn.unitel.campaign.rabbitmq.RabbitMQConsumer;
import mn.unitel.campaign.config.Service;
import mn.unitel.campaign.rabbitmq.RabbitMQProducer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class StartupService {

    private static final Logger logger = Logger.getLogger(StartupService.class);

    @ConfigProperty(name = "app.rabbitmq.consumer.files")
    List<String> consumerFiles;

    @ConfigProperty(name = "app.core.config-dir", defaultValue = "config")
    String configDir;

    @ConfigProperty(name = "app.rabbitmq.producer.sms", defaultValue = "producer-sms-direct.json")
    String smsProducerConfig;

    String environment;

    @Inject ObjectMapper mapper;
    @Inject ConsumerHandlerRegistry handlerRegistry;

    @Getter
    RabbitMQProducer smsProducer;

    private final List<RabbitMQConsumer> activeConsumers = new ArrayList<>();

    void onStart(@Observes StartupEvent ev) {
        Service.init(configDir);

        CustomLoggerFactory.setLoggerType(CustomLoggerFactory.LoggerType.JUL);
        APIUtil.initApiConfig(Utils.fileToJson("config/bssconfig-prod.json"));

        this.environment = Service.getEnvironment();

        smsProducer = new RabbitMQProducer(smsProducerConfig);
        smsProducer.startProduce();

//        consumerFiles.forEach(this::startConsumer);
    }

    private void startConsumer(String jsonFile) {
        String filePath = configDir + "/" + jsonFile;
        File file = new File(filePath);

        if (!file.exists()) {
            logger.errorf("Config file NOT FOUND: %s", filePath);
            return;
        }

        logger.infof("Loading config: %s", filePath);

        try (InputStream is = new FileInputStream(file)) {

            JsonNode root = mapper.readTree(is);
            JsonNode envNode = root.get(environment);

            if (envNode == null) {
                logger.errorf("Environment '%s' missing in %s", environment, filePath);
                return;
            }

            JsonNode queue = envNode.get("exchange").get("queue");
            JsonNode params = queue.get("params");

            String type = extractType(params);
            if (type == null) {
                logger.errorf("Message type missing in params of %s", filePath);
                return;
            }

            var handler = handlerRegistry.get(type.toLowerCase());
            if (handler == null) {
                logger.errorf("No handler registered for '%s' in %s", type, filePath);
                return;
            }

            RabbitMQConsumer consumer = new RabbitMQConsumer(jsonFile, 1, 120);
            consumer.startConsume(handler);
            activeConsumers.add(consumer);

            logger.infof("Started [%s] consumer from %s", type, jsonFile);

        } catch (Exception e) {
            logger.error("Failed consumer startup: " + filePath, e);
        }
    }

    private String extractType(JsonNode params) {
        if (params == null) return null;
        if (params.has("notificationType")) {
            return params.get("notificationType").asText();
        }
        if (params.has("type")) {
            return params.get("type").asText();
        }
        return null;
    }

    void onStop(@Observes ShutdownEvent ev) {
        activeConsumers.forEach(RabbitMQConsumer::stop);
        DBPoolFactory.getInstance().closeAll();
        logger.info("All consumers stopped");
    }
}
