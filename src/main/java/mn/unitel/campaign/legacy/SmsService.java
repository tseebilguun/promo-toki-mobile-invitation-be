package mn.unitel.campaign.legacy;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import mn.unitel.campaign.Utils;
import okhttp3.OkHttpClient;
import org.jboss.logging.Logger;

@ApplicationScoped
public class SmsService {
    @Inject
    StartupService startupService;
    private final OkHttpClient client = new OkHttpClient();

    public void send(String sender, String receiver, String text, boolean sendToTokiMobile) {
        Utils.sendSms(sender, "131401", receiver, text,
                startupService.getSmsProducer(), client, false, sendToTokiMobile);
    }
}
