package org.ctc.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MusicTrackForm {

    @NotBlank
    @Size(max = 150)
    private String title;

    @NotBlank
    @Size(max = 150)
    private String artist;

    @Size(max = 500)
    private String credit;
}
