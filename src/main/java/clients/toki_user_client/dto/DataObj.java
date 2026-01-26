package clients.toki_user_client.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DataObj {
    private String _id;
    private String profilePicURL;
    private String phoneNo;
    private String countryCode;
    private String name;
    private String email;
    private int gender;
}
