package org.ctc.discord;

import jakarta.validation.Validation;
import org.ctc.discord.dto.DiscordConfigForm;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.discord.service.DiscordGlobalConfigService;
import org.ctc.discord.web.DiscordConfigController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ui.ConcurrentModel;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DiscordIncidentThreadConfigTest {
    static final String PARENT="600000000000000006", STAFF="500000000000000005,500000000000000007", TAGS="900000000000000009";
    @Test void givenSavedIncidentDestination_whenRenderingAndSavingTheTypedForm_thenPreserveTheExactExplicitIds() {
        var repository=mock(DiscordGlobalConfigRepository.class);var current=new DiscordGlobalConfig();
        current.setStewardsChannelId(PARENT);current.setIncidentStaffRoleIds(STAFF);current.setStewardsForumTagIds(TAGS);
        when(repository.findFirstByOrderByIdAsc()).thenReturn(current);when(repository.save(current)).thenReturn(current);
        var service=new DiscordGlobalConfigService(repository);
        var controller=new DiscordConfigController(mock(DiscordBotIdentityCache.class),mock(DiscordEmojiCache.class),service,mock(DiscordRestClient.class),mock(DiscordRoleCache.class),mock(DiscordWebhookClient.class));
        var model=new ConcurrentModel();controller.view(model);var form=(DiscordConfigForm)model.getAttribute("form");
        assertThat(form.getStewardsChannelId()).isEqualTo(PARENT);assertThat(form.getIncidentStaffRoleIds()).isEqualTo(STAFF);assertThat(form.getStewardsForumTagIds()).isEqualTo(TAGS);
        form.setStewardsChannelId("600000000000000008");form.setIncidentStaffRoleIds("500000000000000008");form.setStewardsForumTagIds("");service.save(form);
        assertThat(current.getStewardsChannelId()).isEqualTo("600000000000000008");assertThat(current.getIncidentStaffRoleIds()).isEqualTo("500000000000000008");assertThat(current.getStewardsForumTagIds()).isEmpty();
    }
    @Test void givenAnUnconfiguredDestination_whenValidating_thenKeepBootstrapConfigurationValid() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {assertThat(factory.getValidator().validate(new DiscordConfigForm())).isEmpty();}
    }
    @ParameterizedTest @ValueSource(strings={"parent","roles","tags","missing-staff","missing-parent","duplicate-staff","duplicate-tags"})
    void givenMalformedOrIncompleteIncidentDestination_whenValidating_thenRejectTheTypedForm(String cause) {
        var form=new DiscordConfigForm();form.setStewardsChannelId(PARENT);form.setIncidentStaffRoleIds(STAFF);form.setStewardsForumTagIds(TAGS);
        switch(cause) {case "parent" -> form.setStewardsChannelId("private-stewards");case "roles" -> form.setIncidentStaffRoleIds("@everyone");case "tags" -> form.setStewardsForumTagIds("unknown-tag");case "missing-staff" -> form.setIncidentStaffRoleIds("");case "missing-parent" -> form.setStewardsChannelId("");case "duplicate-staff" -> form.setIncidentStaffRoleIds(STAFF+",500000000000000005");case "duplicate-tags" -> form.setStewardsForumTagIds(TAGS+","+TAGS);default -> throw new AssertionError(cause);}
        try(var factory=Validation.buildDefaultValidatorFactory()) {assertThat(factory.getValidator().validate(form)).isNotEmpty();}
    }
}
