package mn.unitel.campaign.models;

import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public enum MobileDataPlan {
    RECHARGE_ENTRY_5GB("34112", 10, 4000, "10737418240", "10GB", "5GB"),
    RECHARGE_ENTRY_10GB("34109", 15, 7000, "21474836480", "20GB", "10GB"),
    RECHARGE_ENTRY_20GB("34110", 30, 12000, "42949672960", "40GB", "20GB"),
    RECHARGE_ENTRY_30GB("34111", 30, 15000, "64424509440", "60GB", "30GB"),
    DEFAULT("UNKNOWN", 0, 0, "0", "0GB", "0GB");

    private static final Map<String, MobileDataPlan> LOOKUP =
            Arrays.stream(values())
                    .collect(Collectors.toUnmodifiableMap(
                            p -> p.entry,
                            p -> p
                    ));

    private final String entry;

    @Getter
    private final int validDays;

    @Getter
    private final int rechargeAmount;

    @Getter
    private final String dataAmount;

    @Getter
    private final String dataAmountStr;

    @Getter
    private final String baseDataAmountStr;

    MobileDataPlan(String entry, int validDays, int rechargeAmount, String dataAmount, String dataAmountStr, String baseDataAmountStr) {
        this.entry = entry;
        this.validDays = validDays;
        this.rechargeAmount = rechargeAmount;
        this.dataAmount = dataAmount;
        this.dataAmountStr = dataAmountStr;
        this.baseDataAmountStr = baseDataAmountStr;
    }

    public static MobileDataPlan fromEntry(String entry) {
        return LOOKUP.getOrDefault(entry, DEFAULT);
    }
}
