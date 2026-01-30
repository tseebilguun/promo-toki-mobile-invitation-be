package mn.unitel.campaign;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import mn.unitel.campaign.jooq.tables.records.PromotionEntitlementsRecord;
import mn.unitel.campaign.jooq.tables.records.ReferralInvitationsRecord;
import mn.unitel.campaign.legacy.SmsService;
import mn.unitel.campaign.models.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.jooq.DSLContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static mn.unitel.campaign.jooq.Tables.REFERRAL_INVITATIONS;
import static mn.unitel.campaign.jooq.Tables.PROMOTION_ENTITLEMENTS;

@Slf4j
@ApplicationScoped
public class MainService {
    private static final Logger logger = Logger.getLogger(MainService.class);

    @Inject
    DSLContext dsl;

    @Inject
    JwtService jwtService;

    @Inject
    Helper helper;

    @Inject
    TokiService tokiService;

    @Inject
    SmsService smsService;

    @ConfigProperty(name = "link.purchase.number")
    String purchaseNumberLink;

    @ConfigProperty(name = "link.referral.number")
    String referralNumberLink;

    public Response getInfoByJwt(@Context ContainerRequestContext ctx) {
        String msisdn = "";
        String tokiId = "";

        try {
            msisdn = (String) ctx.getProperty("jwt.msisdn");
            tokiId = (String) ctx.getProperty("jwt.tokiId");
        } catch (Exception e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new CustomResponse<>("Unauthorized", "Unauthorized", null))
                    .build();
        }

        if (msisdn.isBlank() || tokiId.isBlank()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new CustomResponse<>("Unauthorized", "Unauthorized", null))
                    .build();
        }

        return getInfoGeneral(msisdn, tokiId);
    }

    public Response login(LoginReq loginRequest) {
        if (loginRequest.getMsisdn() == null || loginRequest.getMsisdn().isEmpty() || loginRequest.getTokiId() == null || loginRequest.getTokiId().isEmpty())
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(
                            new CustomResponse<>(
                                    "fail",
                                    "Алдаа гарлаа",
                                    null
                            )
                    )
                    .build();

        if (!helper.getOperatorName(loginRequest.getMsisdn()).equalsIgnoreCase("toki mobile")){
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(
                            new CustomResponse<>(
                                    "fail",
                                    "Зөвхөн Toki Mobile хэрэглэгчид нэвтрэх боломжтой.",
                                    null
                            )
                    )
                    .build();
        }

        return Response.ok().entity(
                        new CustomResponse<>(
                                "success",
                                "Login successful",
                                jwtService.generateTokenWithPhone(
                                        loginRequest.getTokiId(),
                                        loginRequest.getMsisdn(),
                                        loginRequest.getTokiId()
                                )
                        )
                )
                .build();
    }

    public Response sendInvitation(InvitationReq req, @Context ContainerRequestContext ctx) {
        String senderMsisdn = "";
        String senderTokiId = "";

        try {
            senderMsisdn = (String) ctx.getProperty("jwt.msisdn");
            senderTokiId = (String) ctx.getProperty("jwt.tokiId");
        } catch (Exception e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new CustomResponse<>("Unauthorized", "Unauthorized", null))
                    .build();
        }

        if (req.getMsisdn().isBlank() || req.getMsisdn().length() != 8) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(
                            new CustomResponse<>(
                                    "fail",
                                    "Утасны дугаараа шалгаад дахин оролдоно уу.",
                                    null
                            )
                    )
                    .build();
        }

        String receiverMsisdn = req.getMsisdn();

        if (receiverMsisdn.equals(senderMsisdn)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(
                            new CustomResponse<>(
                                    "fail",
                                    "Өөртөө урилга илгээх боломжгүй.",
                                    null
                            )
                    )
                    .build();
        }

        int count = dsl.fetchCount(
                REFERRAL_INVITATIONS,
                REFERRAL_INVITATIONS.RECEIVER_MSISDN.eq(receiverMsisdn)
                        .and(REFERRAL_INVITATIONS.STATUS.in("SENT", "ACCEPTED"))
        );

        if (count > 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(
                            new CustomResponse<>(
                                    "fail",
                                    "Урилга илгээх боломжгүй байна.",
                                    null
                            )
                    )
                    .build();
        }

        if (senderMsisdn.isBlank() || senderTokiId.isBlank()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new CustomResponse<>("Unauthorized", "Unauthorized", null))
                    .build();
        }

        TokiUserInfo receiverTokiInfo = tokiService.getTokiId(receiverMsisdn);

        if (!receiverTokiInfo.isSuccess()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(
                            new CustomResponse<>(
                                    "fail",
                                    "Toki-д бүртгэлгүй хэрэглэгч байна",
                                    null
                            )
                    )
                    .build();
        }

        TokiUserInfo senderTokiInfo = tokiService.getTokiId(senderMsisdn);
        if (!senderTokiInfo.isSuccess()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(
                            new CustomResponse<>(
                                    "fail",
                                    "Алдаа гарлаа. Дахин оролдоно уу.",
                                    null
                            )
                    )
                    .build();
        }

        ReferralInvitationsRecord inserted =
                dsl.insertInto(REFERRAL_INVITATIONS)
                        .set(REFERRAL_INVITATIONS.ID, java.util.UUID.randomUUID()) // required if DB has no default
                        .set(REFERRAL_INVITATIONS.SENDER_MSISDN, senderMsisdn)
                        .set(REFERRAL_INVITATIONS.SENDER_TOKI_ID, senderTokiId)
                        .set(REFERRAL_INVITATIONS.RECEIVER_MSISDN, receiverMsisdn)
                        .set(REFERRAL_INVITATIONS.RECEIVER_TOKI_ID, receiverTokiInfo.getTokiId())
                        .set(REFERRAL_INVITATIONS.STATUS, "SENT")
                        .returning()
                        .fetchOne();

        FormattedDateTime expireDateTime = FormattedDateTime.from(inserted.getExpiresAt());

        tokiService.sendPushNoti(receiverTokiInfo.getTokiId(),
                "Танд " + helper.extractFirstName(senderTokiInfo.getFullName()) + "-с урилга ирлээ",
                senderMsisdn + " дугаартай " + helper.extractFirstName(senderTokiInfo.getFullName()) + " найзаас нь Toki Mobile-д " +
                        "нэгдэх урилга илгээсэн байна. 55-тай дугаар аваад 30 хоногийн турш дата цэнэглэлт бүрээ 3 үржүүлж аваарай",
                purchaseNumberLink,
                "Дугаар авах"

        );


        smsService.send("4477", senderMsisdn, receiverMsisdn + " dugaart Toki Mobile-d negdeh urilga ilgeegdlee. " +
                "Urilgiin huchintei hugatsaa " + expireDateTime.getDate() + "-nii udriin " + expireDateTime.getTime() + "-d duusna shuu.", true);

        return Response.ok()
                .entity(
                        new CustomResponse<>(
                                "Success",
                                "Урилга амжилттай илгээгдлээ.",
                                null
                        )
                ).build();
    }

    public Response getInfo(String msisdn, String tokiId) {
        if (msisdn.isBlank() || tokiId.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new CustomResponse<>("Bad request", "Аль нэг утга хоосон байна.", null))
                    .build();
        }
        return getInfoGeneral(msisdn, tokiId);
    }

    private Response getInfoGeneral(String msisdn, String tokiId) {
        logger.infov("getInfo start: tokiId={0}, msisdn={1}", tokiId, msisdn);

        try {
            LocalDateTime now = LocalDateTime.now();

            int updatedForUser = dsl.update(REFERRAL_INVITATIONS)
                    .set(REFERRAL_INVITATIONS.STATUS, "EXPIRED")
                    .where(REFERRAL_INVITATIONS.SENDER_TOKI_ID.eq(tokiId))
                    .and(REFERRAL_INVITATIONS.SENDER_MSISDN.eq(msisdn))
                    .and(REFERRAL_INVITATIONS.STATUS.eq("SENT"))
                    .and(REFERRAL_INVITATIONS.EXPIRES_AT.lt(now))
                    .execute();

            if (updatedForUser > 0) {
                logger.infov("Expired invitations updated for user: updated={0}, tokiId={1}, msisdn={2}",
                        updatedForUser, tokiId, msisdn);
            }
        } catch (Exception e) {
            logger.errorv(e, "Failed to update expired invitations for user: tokiId={0}, msisdn={1}", tokiId, msisdn);
        }

        try {
            List<ReferralInvitationsRecord> invitations =
                    dsl.selectFrom(REFERRAL_INVITATIONS)
                            .where(REFERRAL_INVITATIONS.SENDER_TOKI_ID.eq(tokiId))
                            .and(REFERRAL_INVITATIONS.SENDER_MSISDN.eq(msisdn))
                            .orderBy(REFERRAL_INVITATIONS.SENT_AT.desc())
                            .fetch();

            logger.debugv("getInfo: fetched invitations count={0} for tokiId={1}, msisdn={2}",
                    invitations.size(), tokiId, msisdn);

            if (invitations.isEmpty()) {
                logger.infov("getInfo: no invitations found for tokiId={0}, msisdn={1}", tokiId, msisdn);

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
            }

            List<Referrals> referrals = invitations.stream()
                    .map(r -> Referrals.builder()
                            .id(r.getId())
                            .invitedNumber(r.getReceiverMsisdn())
                            .newNumber(r.getReceiverNewMsisdn())
                            .status(r.getStatus())
                            .operatorName(helper.getOperatorName(r.getReceiverMsisdn()))
                            .expireDate(r.getExpiresAt())
                            .build())
                    .toList();

            int successReferralsCount = (int) invitations.stream()
                    .filter(r -> "ACCEPTED".equals(r.getStatus()))
                    .count();

            logger.infov("getInfo: referrals={0}, successReferralsCount={1} for tokiId={2}, msisdn={3}",
                    referrals.size(), successReferralsCount, tokiId, msisdn);

            Optional<PromotionEntitlementsRecord> latestEntitlementOpt =
                    dsl.selectFrom(PROMOTION_ENTITLEMENTS)
                            .where(PROMOTION_ENTITLEMENTS.TOKI_ID.eq(tokiId))
                            .and(PROMOTION_ENTITLEMENTS.MSISDN.eq(msisdn))
                            .and(PROMOTION_ENTITLEMENTS.END_AT.isNotNull())
                            .orderBy(PROMOTION_ENTITLEMENTS.END_AT.desc())
                            .fetchOptional();

            LocalDateTime entitlementExpirationDate = latestEntitlementOpt
                    .map(PromotionEntitlementsRecord::getEndAt)
                    .orElse(null);

            boolean hasActiveEntitlement = entitlementExpirationDate != null
                    && entitlementExpirationDate.isAfter(LocalDateTime.now());

            logger.infov("getInfo: entitlement found={0}, hasActiveEntitlement={1}, entitlementExpirationDate={2} for tokiId={3}, msisdn={4}",
                    latestEntitlementOpt.isPresent(),
                    hasActiveEntitlement,
                    entitlementExpirationDate,
                    tokiId,
                    msisdn);

            GetInfoData data = GetInfoData.builder()
                    .referrals(referrals)
                    .hasActiveEntitlement(hasActiveEntitlement)
                    .successReferralsCount(successReferralsCount)
                    .entitlementExpirationDate(entitlementExpirationDate)
                    .build();

            logger.infov("getInfo success: tokiId={0}, msisdn={1}", tokiId, msisdn);

            return Response.ok()
                    .entity(new CustomResponse<>(
                            "Success",
                            "Fetched existing records",
                            data
                    ))
                    .build();

        } catch (Exception e) {
            logger.errorv(e, "getInfo failed: tokiId={0}, msisdn={1}", tokiId, msisdn);

            return Response.serverError()
                    .entity(new CustomResponse<>(
                            "Failed",
                            "Internal server error",
                            null
                    ))
                    .build();
        }
    }

    public Response deleteInvitation(DeleteInvitationReq req, ContainerRequestContext ctx) {
        UUID invitationId = req.getInvitationId();

        String senderMsisdn;
        String senderTokiId;

        try {
            senderMsisdn = (String) ctx.getProperty("jwt.msisdn");
            senderTokiId = (String) ctx.getProperty("jwt.tokiId");
        } catch (Exception e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new CustomResponse<>("Unauthorized", "Unauthorized", null))
                    .build();
        }

        if (senderMsisdn == null || senderMsisdn.isBlank() || senderTokiId == null || senderTokiId.isBlank()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new CustomResponse<>("Unauthorized", "Unauthorized", null))
                    .build();
        }

        ReferralInvitationsRecord deleted =
                dsl.deleteFrom(REFERRAL_INVITATIONS)
                        .where(REFERRAL_INVITATIONS.ID.eq(invitationId))
                        .and(REFERRAL_INVITATIONS.SENDER_MSISDN.eq(senderMsisdn))
                        .and(REFERRAL_INVITATIONS.SENDER_TOKI_ID.eq(senderTokiId))
                        .returning()
                        .fetchOne();

        if (deleted == null) {
            logger.warnv("Referral invitation delete attempted but no row found or not owned. id={0}, senderMsisdn={1}, senderTokiId={2}",
                    invitationId, senderMsisdn, senderTokiId);

            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new CustomResponse<>("fail", "Алдаа гарлаа. Дахин оролдоно уу.", null))
                    .build();
        }

        logger.infov(
                "Referral invitation deleted: id={0}, status={1}, senderMsisdn={2}, senderTokiId={3}, receiverMsisdn={4}, receiverTokiId={5}, sentAt={6}, expiresAt={7}, acceptedAt={8}",
                deleted.getId(),
                deleted.getStatus(),
                deleted.getSenderMsisdn(),
                deleted.getSenderTokiId(),
                deleted.getReceiverMsisdn(),
                deleted.getReceiverTokiId(),
                deleted.getSentAt(),
                deleted.getExpiresAt(),
                deleted.getAcceptedAt()
        );

        return Response.ok()
                .entity(new CustomResponse<>("Success", "Амжилттай устгалаа.", null))
                .build();
    }

    public Response resendInvitation(InvitationReq req, @Context ContainerRequestContext ctx) {
        String senderMsisdn = "";
        String senderTokiId = "";

        try {
            senderMsisdn = (String) ctx.getProperty("jwt.msisdn");
            senderTokiId = (String) ctx.getProperty("jwt.tokiId");
        } catch (Exception e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new CustomResponse<>("Unauthorized", "Unauthorized", null))
                    .build();
        }

        if (req.getMsisdn().isBlank() || req.getMsisdn().length() != 8 || req.getInvitationId() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(
                            new CustomResponse<>(
                                    "fail",
                                    "Утасны дугаараа шалгаад дахин оролдоно уу.",
                                    null
                            )
                    )
                    .build();
        }

        int count = dsl.fetchCount(
                REFERRAL_INVITATIONS,
                REFERRAL_INVITATIONS.RECEIVER_MSISDN.eq(req.getMsisdn())
                        .and(REFERRAL_INVITATIONS.STATUS.in("SENT", "ACCEPTED"))
        );

        if (count > 0) {
            dsl.deleteFrom(REFERRAL_INVITATIONS)
                    .where(REFERRAL_INVITATIONS.ID.eq(req.getInvitationId()))
                    .execute();

            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(
                            new CustomResponse<>(
                                    "fail",
                                    "Урилга илгээх боломжгүй байна.",
                                    null
                            )
                    )
                    .build();
        }

        try {
            LocalDateTime now = LocalDateTime.now();

            int updatedForUser = dsl.update(REFERRAL_INVITATIONS)
                    .set(REFERRAL_INVITATIONS.STATUS, "EXPIRED")
                    .where(REFERRAL_INVITATIONS.ID.eq(req.getInvitationId()))
                    .and(REFERRAL_INVITATIONS.STATUS.eq("SENT"))
                    .and(REFERRAL_INVITATIONS.EXPIRES_AT.lt(now))
                    .execute();

            if (updatedForUser > 0) {
                logger.infov("Expired invitations updated for user: updated={0}, invitationId={1}, msisdn={2}",
                        updatedForUser, req.getInvitationId(), req.getMsisdn());
            }
        } catch (Exception e) {
            logger.errorv(e, "Failed to update expired invitations for user: invitationId={0}, msisdn={1}", req.getInvitationId(), req.getMsisdn());
        }

        TokiUserInfo receiverTokiInfo = tokiService.getTokiId(req.getMsisdn());

        if (!receiverTokiInfo.isSuccess()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(
                            new CustomResponse<>(
                                    "fail",
                                    req.getMsisdn() + " дугаарт Toki хаяг байхгүй байна.",
                                    null
                            )
                    )
                    .build();
        }

        TokiUserInfo senderTokiInfo = tokiService.getTokiId(senderMsisdn);
        if (!senderTokiInfo.isSuccess()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(
                            new CustomResponse<>(
                                    "fail",
                                    "Алдаа гарлаа. Дахин оролдоно уу.",
                                    null
                            )
                    )
                    .build();
        }

        UUID invitationId = req.getInvitationId();
        ReferralInvitationsRecord updated =
                dsl.update(REFERRAL_INVITATIONS)
                        .set(REFERRAL_INVITATIONS.STATUS, "SENT")
                        .set(REFERRAL_INVITATIONS.EXPIRES_AT, LocalDateTime.now().plusDays(3))
                        .set(REFERRAL_INVITATIONS.RECEIVER_TOKI_ID, receiverTokiInfo.getTokiId())
                        .where(REFERRAL_INVITATIONS.ID.eq(invitationId))
                        .and(REFERRAL_INVITATIONS.STATUS.eq("EXPIRED"))
                        .returning()
                        .fetchOne();

        if (updated == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new CustomResponse<>("fail", "Алдаа гарлаа. Дахин оролдоно уу.", null))
                    .build();
        }


        tokiService.sendPushNoti(receiverTokiInfo.getTokiId(),
                "Танд " + helper.extractFirstName(senderTokiInfo.getFullName()) + "-с урилга ирлээ",
                senderMsisdn + " дугаартай " + helper.extractFirstName(senderTokiInfo.getFullName()) + " найзаас нь Toki Mobile-д " +
                        "нэгдэх урилга илгээсэн байна. 55-тай дугаар аваад 30 хоногийн турш дата цэнэглэлт бүрээ 3 үржүүлж аваарай",
                purchaseNumberLink,
                "Дугаар авах"
        );

        FormattedDateTime expireDateTime = FormattedDateTime.from(updated.getExpiresAt());

        smsService.send("4477", senderMsisdn, req.getMsisdn() + " dugaart Toki Mobile-d negdeh urilga ilgeegdlee. " +
                "Urilgiin huchintei hugatsaa " + expireDateTime.getDate() + "-nii udriin " + expireDateTime.getTime() + "-d duusna shuu.", true);


        return Response.ok()
                .entity(
                        new CustomResponse<>(
                                "Success",
                                "Урилга амжилттай илгээгдлээ.",
                                null
                        )
                ).build();


    }
}
