package mn.unitel.campaign;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import mn.unitel.campaign.jooq.tables.records.ReferralInvitationsRecord;
import mn.unitel.campaign.models.CustomResponse;
import mn.unitel.campaign.models.GetInfoData;
import org.jboss.logging.Logger;
import org.jooq.DSLContext;

import java.util.List;

import static mn.unitel.campaign.jooq.Tables.REFERRAL_INVITATIONS;

@ApplicationScoped
public class Services {
    @Inject
    Logger logger;

    @Inject
    DSLContext dsl;

    public Response getInfo(String tokiId) {
        List<ReferralInvitationsRecord> records =
                dsl.selectFrom(REFERRAL_INVITATIONS)
                        .where(REFERRAL_INVITATIONS.SENDER_TOKI_ID.eq(tokiId))
                        .fetch();

        if (records.isEmpty()) {
            return Response.ok()
                    .entity(new CustomResponse<>(
                                    "Success",
                                    "No existing record found",
                                    GetInfoData.builder()
                                            .referrals(List.of())
                                            .entitlementExpirationDate(null)
                                            .hasActiveEntitlement(false)
                                            .successReferralsCount(0)
                                            .build()
                            )
                    )
                    .build();
        } else {
            return null; // TODO build all existing data to CustomResponse with data of GetInfoData.
        }
    }
}
