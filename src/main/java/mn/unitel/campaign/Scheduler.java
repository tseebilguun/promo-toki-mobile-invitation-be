package mn.unitel.campaign;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import mn.unitel.campaign.jooq.tables.records.PromotionEntitlementsRecord;
import mn.unitel.campaign.jooq.tables.records.ReferralInvitationsRecord;
import mn.unitel.campaign.legacy.SmsService;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.List;

import org.jooq.DSLContext;
import org.jooq.Record2;
import org.jooq.Result;

import static mn.unitel.campaign.jooq.Tables.PROMOTION_ENTITLEMENTS;
import static mn.unitel.campaign.jooq.Tables.REFERRAL_INVITATIONS;

@ApplicationScoped
public class Scheduler {

    private static final Logger logger = Logger.getLogger(Scheduler.class);

    @Inject
    DSLContext dsl;

    @Inject
    SmsService smsService;

    @ConfigProperty(name = "link.purchase.number")
    String purchaseNumberLink;

    @ConfigProperty(name = "link.referral.number")
    String referralNumberLink;

    @Inject
    TokiService tokiService;

    @Scheduled(every = "60s")
    public void updateExpiredInvitations() {
        LocalDateTime now = LocalDateTime.now();

        try {
            int updated = dsl.update(REFERRAL_INVITATIONS)
                    .set(REFERRAL_INVITATIONS.STATUS, "EXPIRED")
                    .where(REFERRAL_INVITATIONS.STATUS.eq("SENT"))
                    .and(REFERRAL_INVITATIONS.EXPIRES_AT.lt(now))
                    .execute();

            if (updated > 0) {
                logger.infov("Scheduler expired invitations: updated={0}", updated);
            }
        } catch (Exception e) {
            logger.error("Scheduler failed to expire invitations: " + e.getMessage(), e);
        }
    }

    @Scheduled(every = "60s")
    public void notifyBefore24hBeforeInvitationExpired() {
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime windowStart = now.plusHours(23).plusMinutes(59); // now + 23h59m
        LocalDateTime windowEnd = now.plusHours(24);                  // now + 24h

        try {
            List<Record2<String, String>> rows = dsl
                    .select(REFERRAL_INVITATIONS.SENDER_MSISDN, REFERRAL_INVITATIONS.RECEIVER_MSISDN)
                    .from(REFERRAL_INVITATIONS)
                    .where(REFERRAL_INVITATIONS.STATUS.eq("SENT"))
                    .and(REFERRAL_INVITATIONS.EXPIRES_AT.gt(windowStart))
                    .and(REFERRAL_INVITATIONS.EXPIRES_AT.le(windowEnd))
                    .fetch();

            if (rows.isEmpty()) return;

            for (Record2<String, String> r : rows) {
                String senderMsisdn = r.value1();
                String receiverMsisdn = r.value2();

                String textForSender =
                        receiverMsisdn + " dugaart ilgeesen urilgiin huchintei hugatsaa ni duusah gej " +
                                "baina. Naizdaa sanuulj, uramshuulliin erhee idevhjuuleerei.";

                String textForReceiver = senderMsisdn + " dugaaraas ilgeesen urilgiin huchintei " +
                        "hugatsaa duusah gej baina. Amjij 55-tai dugaaraa " +
                        "avaad uramshuulliin erhee idevhjuuleerei. " +
                        "https://link.toki.mn/CX5z";

                try {
                    smsService.send("4477", senderMsisdn, textForSender, true);
                    smsService.send("4477", receiverMsisdn, textForReceiver, false);
                } catch (Exception smsEx) {
                    logger.errorv(smsEx,
                            "Failed to send 24h reminder SMS. senderMsisdn={0}, receiverMsisdn={1}",
                            senderMsisdn, receiverMsisdn);
                }
            }

        } catch (Exception e) {
            logger.error("24h reminder scheduler failed: " + e.getMessage(), e);
        }
    }

    @Scheduled(every = "60s")
    public void notifyAfterInvitationExpired() {
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime windowStart = now.minusMinutes(1);
        LocalDateTime windowEnd = now;

        try {
            Result<ReferralInvitationsRecord> rows = dsl
                    .selectFrom(REFERRAL_INVITATIONS)
                    .where(REFERRAL_INVITATIONS.EXPIRES_AT.ge(windowStart))
                    .and(REFERRAL_INVITATIONS.EXPIRES_AT.lt(windowEnd))
                    .fetch();

            if (rows.isEmpty()) return;

            for (ReferralInvitationsRecord r : rows) {
                String senderMsisdn = r.getSenderMsisdn();
                String receiverMsisdn = r.getReceiverMsisdn();

                String textForSender =
                        receiverMsisdn + " dugaart ilgeesen urilgiin huchintei hugatsaa duuslaa. " +
                                "Doorh linkeer orj dahin urilga ilgeeh bolomjtoi shuu. https://link.toki.mn/CX5z";

                String textForReceiver =
                        senderMsisdn + " dugaartai naiziin chine ilgeesen Toki Mobile-d negdeh urilgiin hugatsaa duuslaa.";

                try {
                    smsService.send("4477", senderMsisdn, textForSender, true);
                    smsService.send("4477", receiverMsisdn, textForReceiver, true);
                    tokiService.sendPushNoti(
                            r.getReceiverTokiId(),
                            r.getSenderMsisdn() + "-с илгээсэн урилгын хугацаа дууслаа.",
                            r.getSenderMsisdn() + "-с илгээсэн урилгын хугацаа дууслаа.",
                            purchaseNumberLink,
                            "Дугаар авах"
                    );
                } catch (Exception smsEx) {
                    logger.errorv(smsEx,
                            "Failed to send expired notification SMS. senderMsisdn={0}, receiverMsisdn={1}",
                            senderMsisdn, receiverMsisdn);
                }
            }

        } catch (Exception e) {
            logger.error("Expired notification scheduler failed: " + e.getMessage(), e);
        }
    }

    @Scheduled(every = "60s")
    public void notifyAfterEntitlementExpired() {
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime windowStart = now.minusMinutes(1);
        LocalDateTime windowEnd = now;

        try {
            Result<PromotionEntitlementsRecord> rows = dsl
                    .selectFrom(PROMOTION_ENTITLEMENTS)
                    .where(PROMOTION_ENTITLEMENTS.END_AT.ge(windowStart))
                    .and(PROMOTION_ENTITLEMENTS.END_AT.lt(windowEnd))
                    .fetch();


            if (rows.isEmpty()) return;

            for (PromotionEntitlementsRecord r : rows) {
                try {
                    smsService.send("4477", r.getMsisdn(), "Heyo, data 3 urjuulj avah erh n duussan baina shuu. " +
                            "Toki Mobile-iin dugaargui naizaa uriad dahin 30 " +
                            "honogiin tursh data tseneglelt buree 3 urjuulj avah erhtei bolooroi. " + purchaseNumberLink, true);

                    tokiService.sendPushNoti(
                            r.getTokiId(),
                            "Toki Mobile: Дата гурав үржих эрх дууслаа",
                            "Таны дата цэнэглэлт бүрээ 3 үржүүлж авах эрхийн хугацаа " +
                                    "дууслаа. Дахин найзаа уриад урамшууллын эрх аваарай.",
                            referralNumberLink,
                            "Найзаа урих"
                    );
                } catch (Exception smsEx) {
                    logger.errorv(smsEx,
                            "Failed to send expired notification SMS. MSISDN={0}",
                            r.getMsisdn());
                }
            }

        } catch (Exception e) {
            logger.error("Expired notification scheduler failed: " + e.getMessage(), e);
        }
    }

    @Scheduled(every = "60s")
    public void notifyBefore3DaysOfEntitlementExpired() {
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime windowStart = now.plusDays(3).minusMinutes(1);
        LocalDateTime windowEnd = now.plusDays(3);

        try {
            Result<PromotionEntitlementsRecord> rows = dsl
                    .selectFrom(PROMOTION_ENTITLEMENTS)
                    .where(PROMOTION_ENTITLEMENTS.END_AT.ge(windowStart))
                    .and(PROMOTION_ENTITLEMENTS.END_AT.lt(windowEnd))
                    .fetch();

            if (rows.isEmpty()) return;

            for (PromotionEntitlementsRecord r : rows) {
                try {
                    tokiService.sendPushNoti(
                            r.getTokiId(),
                            "Toki Mobile: Дата гурав үржих эрх дуусахад 3 хоног үлдлээ",
                            "Дата цэнэглэлт бүрээ 3 үржүүлж авах эрхийн хугацаа 3 ХОНОГ-ийн дараа дуусах гэж байна. " +
                                    "Дахин найзаа уриад урамшууллын эрхээ 30 хоногоор сунгаарай.",
                            referralNumberLink,
                            "Найзаа урих"
                    );
                } catch (Exception ex) {
                    logger.errorv(ex, "Failed to send 3-day before entitlement expiry push. tokiId={0}, msisdn={1}",
                            r.getTokiId(), r.getMsisdn());
                }
            }
        } catch (Exception e) {
            logger.error("3-day entitlement reminder scheduler failed: " + e.getMessage(), e);
        }
    }

    @Scheduled(every = "60s")
    public void notifyBefore7DaysOfEntitlementExpired() {
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime windowStart = now.plusDays(7).minusMinutes(1);
        LocalDateTime windowEnd = now.plusDays(7);

        try {
            Result<PromotionEntitlementsRecord> rows = dsl
                    .selectFrom(PROMOTION_ENTITLEMENTS)
                    .where(PROMOTION_ENTITLEMENTS.END_AT.ge(windowStart))
                    .and(PROMOTION_ENTITLEMENTS.END_AT.lt(windowEnd))
                    .fetch();

            if (rows.isEmpty()) return;

            for (PromotionEntitlementsRecord r : rows) {
                try {
                    tokiService.sendPushNoti(
                            r.getTokiId(),
                            "Toki Mobile: Дата гурав үржих эрх дуусахад 3 хоног үлдлээ",
                            "Дата цэнэглэлт бүрээ 3 үржүүлж авах эрхийн хугацаа 7 хоногийн дараа дуусах гэж байна. " +
                                    "Дахин найзаа уриад урамшууллын эрхээ 30 хоногоор сунгаарай.",
                            referralNumberLink,
                            "Найзаа урих"
                    );
                } catch (Exception ex) {
                    logger.errorv(ex, "Failed to send 7-day before entitlement expiry push. tokiId={0}, msisdn={1}",
                            r.getTokiId(), r.getMsisdn());
                }
            }
        } catch (Exception e) {
            logger.error("7-day entitlement reminder scheduler failed: " + e.getMessage(), e);
        }
    }
}