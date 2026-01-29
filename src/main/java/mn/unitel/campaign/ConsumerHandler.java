package mn.unitel.campaign;

import clients.dsd_client.DsdClient;
import clients.dsd_client.NumberRelationRes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import mn.unitel.campaign.legacy.SmsService;
import mn.unitel.campaign.models.FormattedDateTime;
import mn.unitel.campaign.models.MobileDataDetail;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;
import org.jooq.DSLContext;

import java.time.LocalDateTime;

import static mn.unitel.campaign.jooq.Tables.PROMOTION_ENTITLEMENTS;
import static mn.unitel.campaign.jooq.Tables.REFERRAL_INVITATIONS;

@ApplicationScoped
public class ConsumerHandler {
    Logger logger = Logger.getLogger(ConsumerHandler.class);

    @Inject
    DSLContext dsl;

    @Inject
    Helper helper;

    @RestClient
    DsdClient dsdClient;

    @Inject
    SmsService smsService;

    @ConfigProperty(name = "link.check.data")
    String link;

    public void gotActive(String msisdn) {
        NumberRelationRes res = dsdClient.getUserId(msisdn);
        if (!res.isSuccess()) {
            logger.infof(res.getResultStr());
            return;
        }

        if (res.getResult().getUserId() == null) {
            logger.infof("User %s is not registered in DSD", msisdn);
            return;
        }

        String receiverTokiId = res.getResult().getUserId();
        LocalDateTime now = LocalDateTime.now();

        var updated = dsl.update(REFERRAL_INVITATIONS)
                .set(REFERRAL_INVITATIONS.RECEIVER_NEW_MSISDN, msisdn)
                .set(REFERRAL_INVITATIONS.ACCEPTED_AT, now)
                .set(REFERRAL_INVITATIONS.STATUS, "ACCEPTED")
                .where(REFERRAL_INVITATIONS.RECEIVER_TOKI_ID.eq(receiverTokiId))
                .and(REFERRAL_INVITATIONS.EXPIRES_AT.gt(now))
                .and(REFERRAL_INVITATIONS.STATUS.eq("SENT"))
                .returning()
                .fetchOne();

        if (updated == null) {
            logger.infof(
                    "No valid invitation to accept for receiverTokiId=%s (msisdn=%s). Either expired/not found/already accepted.",
                    receiverTokiId, msisdn
            );
            return;
        }

        logger.infov(
                "Invitation accepted: id={0}, senderMsisdn={1}, senderTokiId={2}, receiverMsisdn={3}, receiverTokiId={4}, receiverNewMsisdn={5}, acceptedAt={6}",
                updated.getId(),
                updated.getSenderMsisdn(),
                updated.getSenderTokiId(),
                updated.getReceiverMsisdn(),
                updated.getReceiverTokiId(),
                updated.getReceiverNewMsisdn(),
                updated.getAcceptedAt()
        );

        LocalDateTime startAt = now;
        LocalDateTime endAt = now.plusDays(29).withHour(23).withMinute(59).withSecond(59);

        // 1) Referrer (sender)
        var senderPromoInfo = dsl.insertInto(PROMOTION_ENTITLEMENTS)
                .set(PROMOTION_ENTITLEMENTS.TOKI_ID, updated.getSenderTokiId())
                .set(PROMOTION_ENTITLEMENTS.MSISDN, updated.getSenderMsisdn())
                .set(PROMOTION_ENTITLEMENTS.PROMO_TYPE, "Referrer")
                .set(PROMOTION_ENTITLEMENTS.START_AT, startAt)
                .set(PROMOTION_ENTITLEMENTS.END_AT, endAt)
                .returning()
                .fetchOne();

        if (senderPromoInfo == null) {
            logger.error("Failed to insert sender promo info");
            return;
        }

        FormattedDateTime senderPromoExpireDate = FormattedDateTime.from(senderPromoInfo.getEndAt());
        smsService.send(
                "4477",
                updated.getSenderMsisdn(),
                updated.getReceiverMsisdn() + " dugaartai naiz chine urilgiig huleen avch Toki Mobile-d " +
                        "negdlee. " + senderPromoExpireDate.getDate() + "-iig duustal data tseneglelt buree 3 " +
                        "urjuulj avah erh orloo. Toki app-iin Mobile tsesees uramshuulliin erhee shalgaarai. " + link,
                true);

        // 2) Referee (receiver)
        var receiverPromoInfo = dsl.insertInto(PROMOTION_ENTITLEMENTS)
                .set(PROMOTION_ENTITLEMENTS.TOKI_ID, updated.getReceiverTokiId())
                .set(PROMOTION_ENTITLEMENTS.MSISDN, msisdn) // from function param (new active number)
                .set(PROMOTION_ENTITLEMENTS.PROMO_TYPE, "Referee")
                .set(PROMOTION_ENTITLEMENTS.START_AT, startAt)
                .set(PROMOTION_ENTITLEMENTS.END_AT, endAt)
                .returning()
                .fetchOne();

        if (receiverPromoInfo == null) {
            logger.error("Failed to insert receiver promo info");
            return;
        }

        FormattedDateTime receiverPromoExpireDate = FormattedDateTime.from(receiverPromoInfo.getEndAt());
        smsService.send("4477",
                msisdn,
                updated.getSenderMsisdn() + " dugaartai naizaas chine iruulsen urilgiin " +
                "daguu " + receiverPromoExpireDate.getDate() + "-nii udriin " + receiverPromoExpireDate.getTime() + " hurtel " +
                "data avah burdee 3 urjuulj avah uramshuulliin erh " +
                "orloo. Erh shalgah: " + link,
                true);
    }

    public void onRecharge(String msisdn, String rechargedProduct) {
        if (!helper.isTokiNumber(msisdn)) {
            logger.info(msisdn + " is not Toki Mobile number");
            return;
        }

        logger.infof("Activating bonus for %s with recharge of %s", msisdn, rechargedProduct);

        MobileDataDetail dataDetail = new MobileDataDetail(rechargedProduct);

        if (dataDetail.getDataAmountStr().equalsIgnoreCase("no_data")) {
            logger.infof("Bonus won't be multiplied for %s. Recharged Product is %s", msisdn, rechargedProduct);
            return;
        }
    }
}
