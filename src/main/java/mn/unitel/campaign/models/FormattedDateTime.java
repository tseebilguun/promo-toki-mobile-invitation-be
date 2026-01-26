package mn.unitel.campaign.models;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
@Builder
public class FormattedDateTime {
    private String date;
    private String time;

    public static FormattedDateTime from(LocalDateTime dateTime) {
        if (dateTime == null) return null;

        return FormattedDateTime.builder()
                .date(dateTime.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")))
                .time(dateTime.format(DateTimeFormatter.ofPattern("HH:mm")))
                .build();
    }
}