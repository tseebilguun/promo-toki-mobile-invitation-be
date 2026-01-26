package clients.toki_general;

import lombok.Data;

@Data
public class TokiNotiGetTokenRes {
    private int code;
    private String status;
    private long timestamp;
    private DataField data;
    private String error; // Assuming error can be null or any type of object

    @Data
    public static class DataField {
        private String accessToken;
    }
}
