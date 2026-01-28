package mn.unitel.campaign;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import mn.unitel.campaign.legacy.SmsService;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.List;

import org.jooq.DSLContext;
import org.jooq.Record2;

import static mn.unitel.campaign.jooq.Tables.REFERRAL_INVITATIONS;

@ApplicationScoped
public class Scheduler {

    private static final Logger logger = Logger.getLogger(Scheduler.class);

    @Inject
    DSLContext dsl;

    @Inject
    SmsService smsService;

    @Inject
    TokiService tokiService;

    @Scheduled(every = "60s")
    void expireSentInvitations() {
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
    void reminderBefore24Hours() {
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
    void notifyRightAfterExpired() {
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime windowStart = now.minusMinutes(1);
        LocalDateTime windowEnd = now;

        try {
            List<Record2<String, String>> rows = dsl
                    .select(REFERRAL_INVITATIONS.SENDER_MSISDN, REFERRAL_INVITATIONS.RECEIVER_MSISDN)
                    .from(REFERRAL_INVITATIONS)
                    .where(REFERRAL_INVITATIONS.STATUS.eq("SENT"))
                    .and(REFERRAL_INVITATIONS.EXPIRES_AT.ge(windowStart))
                    .and(REFERRAL_INVITATIONS.EXPIRES_AT.lt(windowEnd))
                    .fetch();

            if (rows.isEmpty()) return;

            for (Record2<String, String> r : rows) {
                String senderMsisdn = r.value1();
                String receiverMsisdn = r.value2();

                String textForSender =
                        receiverMsisdn + " dugaart ilgeesen urilgiin huchintei hugatsaa duuslaa. " +
                                "Doorh linkeer orj dahin urilga ilgeeh bolomjtoi shuu. https://link.toki.mn/CX5z";

                String textForReceiver =
                        senderMsisdn + " dugaartai naiziin chine ilgeesen Toki Mobile-d negdeh urilgiin hugatsaa duuslaa.";

                try {
                    smsService.send("4477", senderMsisdn, textForSender, true);
                    smsService.send("4477", receiverMsisdn, textForReceiver, false);
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
}