package mn.unitel.campaign.legacy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import mn.unitel.campaign.ConsumerHandler;
import mn.unitel.campaign.helpers.RechargeNoti;
import mn.unitel.campaign.helpers.SmsNoti;
import mn.unitel.campaign.helpers.StatusNoti;
import mn.unitel.campaign.rabbitmq.util.RabbitMQSmsMessage;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;

@Slf4j
@ApplicationScoped
public class Consumers {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    CampaignConfig campaignConfig;

    @Inject
    ConsumerHandler consumerHandler;

    private static final Logger logger = Logger.getLogger(Consumers.class.getName());

    public void onStatusChange(byte[] bytes) {
        try {
            JsonNode json = objectMapper.readTree(bytes);
            logger.info("Status Change received");

            StatusNoti statusNoti = new StatusNoti(json);
            logger.info(statusNoti.toString());

            String msisdn = statusNoti.getPhoneNo();

            if (!campaignConfig.shouldProcess(msisdn))
                return;

            if (!campaignConfig.isWithinCampaignPeriod(LocalDateTime.now()))
                return;

            if (!(statusNoti.getFormerStatus().equals("Pending Activation") && statusNoti.getCurrentStatus().equals("Active")))
                return;


            consumerHandler.gotActive(msisdn, statusNoti.getAccountName());

        } catch (Exception e) {
            logger.error("JSON parsing failed", e);
        }
    }

    public void onSms(byte[] bytes) {
        SmsNoti smsNoti = new SmsNoti(bytes);

        if (!smsNoti.isValid())
            return;

        logger.info(smsNoti.toString());

        if (!campaignConfig.shouldProcess(smsNoti.getSender()))
            return;

        if (!campaignConfig.isWithinCampaignPeriod(LocalDateTime.now()))
            return;

        consumerHandler.onSms(smsNoti.getSender(), smsNoti.getText());
    }
}
