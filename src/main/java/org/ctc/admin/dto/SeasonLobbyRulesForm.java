package org.ctc.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.ctc.domain.model.LobbyRulesProfile;

@Getter
@Setter
public class SeasonLobbyRulesForm {
    @NotNull private LobbyRulesProfile expectedProfile;
    @NotNull private LobbyRulesProfile lobbyRulesProfile;

    public static SeasonLobbyRulesForm from(LobbyRulesProfile profile) {
        var form = new SeasonLobbyRulesForm();
        form.expectedProfile = profile; form.lobbyRulesProfile = profile;
        return form;
    }
}
