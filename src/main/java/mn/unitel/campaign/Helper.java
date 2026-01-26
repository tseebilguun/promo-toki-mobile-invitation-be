package mn.unitel.campaign;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class Helper {
    public String getOperatorName(String msisdn) {
        if (msisdn == null || msisdn.length() < 4) {
            return null;
        }

        int prefix4 = Integer.parseInt(msisdn.substring(0, 4));
        int prefix2 = Integer.parseInt(msisdn.substring(0, 2));

        // Toki Mobile
        if ((prefix4 >= 5000 && prefix4 <= 5049) ||
                (prefix4 >= 5500 && prefix4 <= 5549)) {
            return "Toki Mobile";
        }

        // Kaos
        if ((prefix4 >= 5050 && prefix4 <= 5099) ||
                (prefix4 >= 5550 && prefix4 <= 5599)) {
            return "Kaos";
        }

        // Unitel
        if (prefix2 == 88 || prefix2 == 80 || prefix2 == 86 || prefix2 == 89) {
            return "Unitel";
        }

        // Mobicom
        if (prefix2 == 99 || prefix2 == 94 || prefix2 == 95 || prefix2 == 85) {
            return "Mobicom";
        }

        // Skytel
        if (prefix2 == 90 || prefix2 == 91 || prefix2 == 92 || prefix2 == 96 || prefix2 == 69) {
            return "Skytel";
        }

        // GMobile
        if (prefix2 == 93 || prefix2 == 97 || prefix2 == 98 || prefix2 == 83) {
            return "GMobile";
        }

        return null;
    }

    public String extractFirstName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return null;
        }

        String[] parts = fullName.trim().split("\\s+");
        return parts[0];
    }
}
