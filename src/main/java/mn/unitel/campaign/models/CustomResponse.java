package mn.unitel.campaign.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
@Builder
public class CustomResponse<T> {
    private String result;
    private String message;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private T data;
}
