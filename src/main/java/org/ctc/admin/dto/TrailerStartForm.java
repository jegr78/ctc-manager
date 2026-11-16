package org.ctc.admin.dto;

import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Start request for a trailer; an empty music ID picks a random track. */
@Getter
@Setter
public class TrailerStartForm {
    private UUID musicId;
}
