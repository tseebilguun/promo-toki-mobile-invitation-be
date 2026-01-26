package mn.unitel.campaign.models;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Getter
@Setter
@Builder
public class GetInfoData {
    List<Referrals> referrals;
    boolean hasActiveEntitlement;
    int successReferralsCount;
    LocalDateTime entitlementExpirationDate;

}
