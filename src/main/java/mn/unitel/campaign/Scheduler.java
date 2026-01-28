package mn.unitel.campaign;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;

import org.jooq.DSLContext;

import static mn.unitel.campaign.jooq.Tables.REFERRAL_INVITATIONS;

@ApplicationScoped
public class Scheduler {

    private static final Logger logger = Logger.getLogger(Scheduler.class);

    @Inject
    DSLContext dsl;

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
}