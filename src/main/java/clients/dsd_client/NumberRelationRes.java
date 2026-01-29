package clients.dsd_client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class NumberRelationRes {
    private String resultCode;
    private String resultStr;
    private Result result;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private String userId;
        private String reason;
        private String detail;
    }

    public boolean isSuccess() {
        return "000".equals(resultCode);
    }

    public String getUserIdSafe() {
        return result != null ? result.getUserId() : null;
    }
}
