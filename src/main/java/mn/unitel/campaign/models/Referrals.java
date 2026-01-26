package mn.unitel.campaign.models;

import java.time.LocalDateTime;
import java.util.UUID;

public class Referrals {
    UUID id;
    String invitedNumber;
    String newNumber;
    String status;
    String operatorName;
    LocalDateTime expireDate;
}
