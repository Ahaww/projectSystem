package edu.wylie.crs.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import edu.wylie.crs.domain.ItemStatus;
import edu.wylie.crs.domain.ItemType;
import edu.wylie.crs.domain.ScheduleStatus;

import java.util.List;

public class ScheduleDtos {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ScheduleItemVO(
            String offeringId,
            ItemType type,
            ItemStatus status,
            Integer priority
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ScheduleVO(
            ScheduleStatus status,
            List<ScheduleItemVO> items,
            String submitTime
    ) {
    }

    public record SaveScheduleRequest(List<ScheduleItemVO> items) {
    }
}
