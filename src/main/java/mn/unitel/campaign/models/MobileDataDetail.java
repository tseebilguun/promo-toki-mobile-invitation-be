package mn.unitel.campaign.models;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
@Getter
@Setter
public class MobileDataDetail {
    String expireDate;
    int rechargeAmount;
    String dataAmount;
    String dataAmountStr;
    String baseDataAmountStr;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public MobileDataDetail(String entry) {
        MobileDataPlan plan = MobileDataPlan.fromEntry(entry);

        LocalDateTime expiry = LocalDateTime.now()
                .plusDays(plan.getValidDays())
                .withHour(23)
                .withMinute(59)
                .withSecond(58);

        this.expireDate = expiry.format(FORMATTER);
        this.rechargeAmount = plan.getRechargeAmount();
        this.dataAmount = plan.getDataAmount();
        this.dataAmountStr = plan.getDataAmountStr();
        this.baseDataAmountStr = plan.getBaseDataAmountStr();
    }
}
