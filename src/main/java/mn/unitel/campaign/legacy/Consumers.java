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
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@ApplicationScoped
public class Consumers {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    CampaignConfig campaignConfig;

    @Inject
    ConsumerHandler consumerHandler;

    @ConfigProperty(name = "recharge.noti.accepted.offers")
    List<String> acceptedRechargeOffers;

    @ConfigProperty (name = "status.noti.accepted.offers")
    List<String> acceptedNewNumberOffers;

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

            if (!acceptedNewNumberOffers.contains(statusNoti.getEntryLevelOffer())) {
                logger.infof("Offer %s is not in accepted new number offers list", statusNoti.getEntryLevelOffer());
                return;
            }

            consumerHandler.gotActive(msisdn);

        } catch (Exception e) {
            logger.error("JSON parsing failed", e);
        }
    }

    public void onRecharge(byte[] bytes) {
        try {
            JsonNode json = objectMapper.readTree(bytes);
            logger.info("Child offer creation received");

            RechargeNoti rechargeNoti = new RechargeNoti(json);
            logger.info(rechargeNoti.toString());

            String msisdn = rechargeNoti.getRechargedNumber();

            if (!campaignConfig.shouldProcess(msisdn))
                return;

            if (!campaignConfig.isWithinCampaignPeriod(LocalDateTime.now()))
                return;

            if (!acceptedRechargeOffers.contains(rechargeNoti.getEntryLevelOffer())) {
                logger.infof("Offer %s is not in accepted recharge offers list", rechargeNoti.getEntryLevelOffer());
                return;
            }

            consumerHandler.onRecharge(msisdn, rechargeNoti.getEntryLevelOffer());

        } catch (Exception e) {
            logger.error("JSON parsing failed", e);
        }
    }
}
