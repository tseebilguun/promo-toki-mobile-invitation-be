package mn.unitel.campaign.legacy;

import jakarta.inject.Singleton;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.List;

@Singleton
@Getter
public class CampaignConfig {
    private static final Logger logger = Logger.getLogger(CampaignConfig.class);

    @ConfigProperty(name = "campaign.start", defaultValue = "2023-01-01T00:00:00")
    LocalDateTime startDate;

    @ConfigProperty(name = "campaign.end", defaultValue = "2099-12-31T23:59:59")
    LocalDateTime endDate;

    @ConfigProperty(name = "campaign.test.numbers", defaultValue = "89115441")
    List<String> testNumbers;

    @ConfigProperty(name = "campaign.debug.mode", defaultValue = "false")
    boolean debugMode;

    public boolean shouldProcess(String msisdn) {
        boolean isTest = testNumbers.contains(msisdn);

        if (debugMode) {
            if (isTest) {
                logger.infof("✅ Debug mode: allowing TEST number %s", msisdn);
                return true;
            } else {
                logger.warnf("🚫 Debug mode: BLOCKING NON-TEST number %s", msisdn);
                return false;
            }
        }

        return true;
    }

    public boolean isWithinCampaignPeriod(LocalDateTime dateTime) {
        if (dateTime.isAfter(startDate) && dateTime.isBefore(endDate)) {
            return true;
        } else {
            logger.infof("🚫 Outside campaign period: %s not in [%s - %s]", dateTime, startDate, endDate);
            return false;
        }
    }
}
