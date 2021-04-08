package org.opensilex.core.position.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.swagger.annotations.ApiModelProperty;
import org.opensilex.core.organisation.api.facitity.InfrastructureFacilityNamedDTO;

import java.net.URI;
import java.time.OffsetDateTime;
import org.opensilex.core.event.dal.move.MoveEventNoSqlModel;
import org.opensilex.core.event.dal.move.MoveModel;
import org.opensilex.core.event.dal.move.PositionModel;

public class PositionGetDTO {

    @JsonProperty("event")
    private URI event;

    @JsonProperty("move_time")
    private String moveTime;

    @JsonProperty("from")
    private InfrastructureFacilityNamedDTO from;

    @JsonProperty("to")
    private InfrastructureFacilityNamedDTO to;

    @JsonProperty("position")
    private PositionGetDetailDTO position;

    public PositionGetDTO(MoveModel model, PositionModel position) throws JsonProcessingException {

        event = model.getUri();
        if (model.getFrom() != null) {
            from = new InfrastructureFacilityNamedDTO(model.getFrom());
        }
        if (model.getTo() != null) {
            to = new InfrastructureFacilityNamedDTO(model.getTo());
        }
        moveTime = model.getEnd().getDateTimeStamp().toString();

        if (position != null) {
            this.position = new PositionGetDetailDTO(position);
        }
    }

    public PositionGetDTO() {

    }

    @ApiModelProperty(value = "Move event which update the position", example = "http://www.opensilex.org/move/12590c87-1c34-426b-a231-beb7acb33415")
    public URI getEvent() {
        return event;
    }

    public void setEvent(URI event) {
        this.event = event;
    }

    @ApiModelProperty(value = "Move time", example = "2019-09-08T12:00:00+01:00")
    public String getMoveTime() {
        return moveTime;
    }

    public void setMoveTime(String moveTime) {
        this.moveTime = moveTime;
    }

    public InfrastructureFacilityNamedDTO getFrom() {
        return from;
    }

    public void setFrom(InfrastructureFacilityNamedDTO from) {
        this.from = from;
    }

    public InfrastructureFacilityNamedDTO getTo() {
        return to;
    }

    public void setTo(InfrastructureFacilityNamedDTO to) {
        this.to = to;
    }

    public PositionGetDetailDTO getPosition() {
        return position;
    }

    public void setPosition(PositionGetDetailDTO position) {
        this.position = position;
    }
}
