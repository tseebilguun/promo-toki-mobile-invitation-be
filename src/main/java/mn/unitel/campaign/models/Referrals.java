package mn.unitel.campaign.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Getter
@AllArgsConstructor
@Builder
public class Referrals {
    UUID id;
    String invitedNumber;
    String newNumber;
    String status;
    String operatorName;
    LocalDateTime expireDate;
}
