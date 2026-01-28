package mn.unitel.campaign.legacy;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.function.Consumer;

@ApplicationScoped
public class ConsumerHandlerRegistry {

    private final Map<String, Consumer<byte[]>> handlers;

    @Inject
    public ConsumerHandlerRegistry(Consumers c) {
        handlers = Map.of(
                "recharge", c::onRecharge,
                "base_product_status_change", c::onStatusChange
        );
    }

    public Consumer<byte[]> get(String type) {
        return handlers.get(type);
    }
}
