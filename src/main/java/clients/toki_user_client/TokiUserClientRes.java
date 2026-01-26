package clients.toki_user_client;

import clients.toki_user_client.dto.DataObj;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class TokiUserClientRes {
    private int statusCode;
    private String error;
    private String responseType;
    private String message;
    private DataObj data;
    private String type;
}
