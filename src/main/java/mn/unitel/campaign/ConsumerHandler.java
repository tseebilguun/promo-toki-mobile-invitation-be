package mn.unitel.campaign;

import Executable.APIUtil;
import clients.dsd_client.DsdClient;
import clients.dsd_client.NumberRelationRes;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import mn.unitel.campaign.jooq.tables.records.PromotionEntitlementsRecord;
import mn.unitel.campaign.legacy.SmsService;
import mn.unitel.campaign.models.FormattedDateTime;
import mn.unitel.campaign.models.MobileDataDetail;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;
import org.jooq.DSLContext;

import java.time.LocalDateTime;

import static mn.unitel.campaign.jooq.Tables.*;

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

    @ConfigProperty(name = "link.purchase.number")
    String purchaseNumberLink;

    @ConfigProperty(name = "campaign.debug.mode", defaultValue = "false")
    boolean debugMode;

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
        var senderPromoInfo = upsertOrExtendEntitlement(
                updated.getSenderTokiId(),
                updated.getSenderMsisdn(),
                "Referrer",
                now
        );

        if (senderPromoInfo == null) {
            logger.error("Failed to upsert/extend sender promo info");
            return;
        }

        FormattedDateTime senderPromoExpireDate = FormattedDateTime.from(senderPromoInfo.getEndAt());
        smsService.send(
                "4477",
                updated.getSenderMsisdn(),
                updated.getReceiverMsisdn() + " dugaartai naiz chine urilgiig huleen avch Toki Mobile-d " +
                        "negdlee. " + senderPromoExpireDate.getDate() + "-iig duustal data tseneglelt buree 3 " +
                        "urjuulj avah erh orloo. Toki app-iin Mobile tsesees uramshuulliin erhee shalgaarai. " + purchaseNumberLink,
                true);

        // 2) Referee (receiver)
        var receiverPromoInfo = upsertOrExtendEntitlement(
                updated.getReceiverTokiId(),
                msisdn,
                "Referee",
                now
        );

        if (receiverPromoInfo == null) {
            logger.error("Failed to upsert/extend receiver promo info");
            return;
        }

        FormattedDateTime receiverPromoExpireDate = FormattedDateTime.from(receiverPromoInfo.getEndAt());
        smsService.send("4477",
                msisdn,
                updated.getSenderMsisdn() + " dugaartai naizaas chine iruulsen urilgiin " +
                        "daguu " + receiverPromoExpireDate.getDate() + "-nii udriin " + receiverPromoExpireDate.getTime() + " hurtel " +
                        "data avah burdee 3 urjuulj avah uramshuulliin erh " +
                        "orloo. Erh shalgah: " + purchaseNumberLink,
                true);
    }

    private PromotionEntitlementsRecord upsertOrExtendEntitlement(String tokiId,
                                                                  String msisdn,
                                                                  String promoType,
                                                                  LocalDateTime now) {
        // Find existing row (prefer an active one)
        PromotionEntitlementsRecord existing = dsl.selectFrom(PROMOTION_ENTITLEMENTS)
                .where(PROMOTION_ENTITLEMENTS.TOKI_ID.eq(tokiId))
                .and(PROMOTION_ENTITLEMENTS.MSISDN.eq(msisdn))
                .and(PROMOTION_ENTITLEMENTS.PROMO_TYPE.eq(promoType))
                .and(PROMOTION_ENTITLEMENTS.END_AT.ge(now))
                .orderBy(PROMOTION_ENTITLEMENTS.END_AT.desc())
                .fetchOne();

        if (existing != null) {
            LocalDateTime newEndAt = existing.getEndAt().plusDays(30);

            return dsl.update(PROMOTION_ENTITLEMENTS)
                    .set(PROMOTION_ENTITLEMENTS.END_AT, newEndAt)
                    .where(PROMOTION_ENTITLEMENTS.ID.eq(existing.getId()))
                    .returning()
                    .fetchOne();
        }

        LocalDateTime startAt = now;
        LocalDateTime endAt = now.plusDays(29).withHour(23).withMinute(59).withSecond(59);

        return dsl.insertInto(PROMOTION_ENTITLEMENTS)
                .set(PROMOTION_ENTITLEMENTS.TOKI_ID, tokiId)
                .set(PROMOTION_ENTITLEMENTS.MSISDN, msisdn)
                .set(PROMOTION_ENTITLEMENTS.PROMO_TYPE, promoType)
                .set(PROMOTION_ENTITLEMENTS.START_AT, startAt)
                .set(PROMOTION_ENTITLEMENTS.END_AT, endAt)
                .returning()
                .fetchOne();
    }

    public void onRecharge(String msisdn, String rechargedProduct) {
        if (!helper.isTokiNumber(msisdn)) {
            logger.info(msisdn + " is not Toki Mobile number");
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        var activeEntitlement = dsl.selectFrom(PROMOTION_ENTITLEMENTS)
                .where(PROMOTION_ENTITLEMENTS.MSISDN.eq(msisdn))
                .and(PROMOTION_ENTITLEMENTS.START_AT.le(now))
                .and(PROMOTION_ENTITLEMENTS.END_AT.ge(now))
                .fetchOne();

        if (activeEntitlement == null) {
            logger.infof("No active entitlement found. Bonus won't be multiplied for %s.", msisdn);
            return;
        }

        logger.infof("Activating bonus for %s with recharge of %s", msisdn, rechargedProduct);

        MobileDataDetail dataDetail = new MobileDataDetail(rechargedProduct);

        if (dataDetail.getDataAmountStr().equalsIgnoreCase("no_data")) {
            logger.infof("Bonus won't be multiplied for %s. Recharged Product is %s", msisdn, rechargedProduct);
            return;
        }

        JsonNode response = Utils.toJsonNode(
                APIUtil.addDeleteProduct(
                        msisdn,
                        "campaign_data",
                        dataDetail.getDataAmount(),
                        "A26_003",
                        dataDetail.getExpireDate(),
                        "Campaign",
                        "add",
                        debugMode));

        boolean success = response != null && response.path("result").asText().equalsIgnoreCase("success");

        dsl.insertInto(RECHARGES)
                .set(RECHARGES.TOKI_ID, activeEntitlement.getTokiId())
                .set(RECHARGES.MSISDN, msisdn)
                .set(RECHARGES.AMOUNT_MNT, dataDetail.getRechargeAmount())
                .set(RECHARGES.BASE_GB, dataDetail.getBaseDataAmountStr())
                .set(RECHARGES.BONUS_GB, dataDetail.getDataAmountStr())
                .set(RECHARGES.BONUS_RESULT, success ? "Success" : "Fail")
                .set(RECHARGES.BONUS_RESPONSE, response != null ? response.toString() : null)
                .set(RECHARGES.RECHARGED_AT, now)
                .execute();

        if (success) {
            logger.infof("Bonus activated for %s with recharge of %s", msisdn, rechargedProduct);
            smsService.send("4477", msisdn, "Naizaa urih uramshuulliin " + dataDetail.getDataAmountStr() + " bonus data " +
                    "idevhejlee. Data uldegdlee Toki app-r shalgaarai. " + purchaseNumberLink, true);
        } else
            logger.errorf("Bonus activation failed for %s with recharge of %s", msisdn, rechargedProduct);

    }
}
