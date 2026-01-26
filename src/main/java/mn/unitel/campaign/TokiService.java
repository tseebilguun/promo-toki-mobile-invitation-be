package mn.unitel.campaign;

import clients.toki_general.TokiGeneralClient;
import clients.toki_general.TokiNotiReq;
import clients.toki_user_client.TokiUserClient;
import clients.toki_user_client.TokiUserClientRes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import mn.unitel.campaign.models.TokiUserInfo;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

@ApplicationScoped
public class TokiService {
    @Inject
    Logger logger;

    @RestClient
    TokiUserClient tokiUserClient;

    @RestClient
    TokiGeneralClient tokiGeneralClient;

    public TokiUserInfo getTokiId(String msisdn) {
        try {
            TokiUserClientRes res = tokiUserClient.getUserDetails(msisdn);
            return TokiUserInfo.builder()
                    .success(true)
                    .tokiId(res.getData().get_id())
                    .fullName(res.getData().getName())
                    .build();
        } catch (Exception e) {
            logger.errorv(e, "Failed to get Toki ID for msisdn={0}", msisdn);
            return TokiUserInfo.builder()
                    .tokiId("NOT_FOUND")
                    .fullName("NOT_FOUND")
                    .success(false)
                    .build();
        }
    }

    public void sendPushNoti(String tokiId, String title, String body) {
        logger.info("Sending toki noti to user: " + tokiId);
        String token = "Bearer " + tokiGeneralClient.getToken().getData().getAccessToken();

        try {
            tokiGeneralClient.send(
                    token,
                    TokiNotiReq.builder()
                            .title(title)
                            .body(body)
                            .url("https://link.toki.mn/CX5z") // TODO Change
                            .buttonName("OK")
                            .accountId(tokiId)
                            .icon("test")
                            .merchantId("66a71d8328f4dda2cd2b1d9d")
                            .build());
        } catch (Exception e) {
            logger.error("Failed to send push noti to Toki ID: " + tokiId + ", " + e.getMessage());
        }
    }
}
