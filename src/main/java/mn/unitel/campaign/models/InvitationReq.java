package mn.unitel.campaign.models;

import lombok.Data;

import java.util.UUID;

@Data
public class InvitationReq {
    String msisdn;
    UUID invitationId;
}
