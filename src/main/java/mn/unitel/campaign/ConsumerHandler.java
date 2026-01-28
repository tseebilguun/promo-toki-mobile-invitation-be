package mn.unitel.campaign;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import mn.unitel.campaign.models.MobileDataDetail;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ConsumerHandler {
    Logger logger = Logger.getLogger(ConsumerHandler.class);

    @Inject
    Helper helper;

    public void gotActive(String msisdn, String accountName) {

    }

    public void onRecharge(String msisdn, String rechargedProduct) {
        if (!helper.isTokiNumber(msisdn)) {
            logger.info(msisdn + " is not Toki Mobile number");
            return;
        }

        logger.infof("Activating bonus for %s with recharge of %s", msisdn, rechargedProduct);

        MobileDataDetail dataDetail = new MobileDataDetail(rechargedProduct);

        if (dataDetail.getDataAmountStr().equalsIgnoreCase("no_data")){
            logger.infof("Bonus won't be multiplied for %s. Recharged Product is %s", msisdn, rechargedProduct);
            return;
        }
    }
}
