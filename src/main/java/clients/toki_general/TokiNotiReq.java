package clients.toki_general;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Builder
@Getter
@Setter
public class TokiNotiReq {
    private String title;
    private String body;
    private String url;
    private String buttonName;
    private String accountId;
    private String icon;
    private String merchantId;
}