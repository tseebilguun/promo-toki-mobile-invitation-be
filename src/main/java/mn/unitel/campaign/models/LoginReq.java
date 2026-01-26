package mn.unitel.campaign.models;

import lombok.Data;

@Data
public class LoginReq extends BaseJsonModel {
    String msisdn;
    String tokiId;
}