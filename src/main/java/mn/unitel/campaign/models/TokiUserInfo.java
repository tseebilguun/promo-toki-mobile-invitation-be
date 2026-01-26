package mn.unitel.campaign.models;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class TokiUserInfo {
    boolean success;
    String tokiId;
    String fullName;
}
