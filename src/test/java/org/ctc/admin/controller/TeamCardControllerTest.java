package org.ctc.admin.controller;

import org.ctc.TestHelper;
import org.ctc.admin.service.TeamCardService;
import org.ctc.domain.model.SeasonTeam;
import org.ctc.domain.repository.SeasonTeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class TeamCardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestHelper testHelper;

    @Autowired
    private SeasonTeamRepository seasonTeamRepository;

    @MockitoBean
    private TeamCardService teamCardService;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @Test
    void whenGetTeamCards_thenReturnsTeamCardsView() throws Exception {
        // when
        mockMvc.perform(get("/admin/tools/team-cards"))
                // then
                .andExpect(status().isOk())
                .andExpect(view().name("admin/team-cards"))
                .andExpect(model().attributeExists("seasons"));
    }

    @Test
    void givenSeasonId_whenGetTeamCardsWithFilter_thenReturnsFilteredView() throws Exception {
        // given
        var season = testHelper.createSeason("Test_TeamCard Season");
        season.setActive(true);

        // when
        mockMvc.perform(get("/admin/tools/team-cards")
                        .param("seasonId", season.getId().toString()))
                // then
                .andExpect(status().isOk())
                .andExpect(view().name("admin/team-cards"))
                .andExpect(model().attributeExists("seasons", "season", "cardStates", "selectedSeasonId"));
    }

    @Test
    void givenActiveSeason_whenGetTeamCards_thenReturnsTeamCardsView() throws Exception {
        // given
        var season = testHelper.createSeason("Test_TeamCard Active Season");
        season.setActive(true);

        // when
        mockMvc.perform(get("/admin/tools/team-cards"))
                // then
                .andExpect(status().isOk())
                .andExpect(view().name("admin/team-cards"))
                .andExpect(model().attributeExists("seasons"));
    }

    @Test
    void givenSeasonTeam_whenGenerateCard_thenRedirectsWithSuccess() throws Exception {
        // given
        var season = testHelper.createSeason("Test_TC_Gen Season");
        var team = testHelper.createTeam("TC Gen Team", "TCG");
        season.addTeam(team);
        var seasonTeam = seasonTeamRepository.findBySeasonIdAndTeamId(season.getId(), team.getId()).orElseThrow();

        when(teamCardService.generateCard(any(SeasonTeam.class))).thenReturn("/uploads/team-cards/test.png");

        // when
        mockMvc.perform(post("/admin/tools/team-cards/generate/" + seasonTeam.getId()))
                // then
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tools/team-cards?seasonId=" + season.getId()))
                .andExpect(flash().attributeExists("successMessage"));

        verify(teamCardService).generateCard(any(SeasonTeam.class));
    }

    @Test
    void givenServiceThrowsException_whenGenerateCard_thenRedirectsWithError() throws Exception {
        // given
        var season = testHelper.createSeason("Test_TC_Err Season");
        var team = testHelper.createTeam("TC Err Team", "TCE");
        season.addTeam(team);
        var seasonTeam = seasonTeamRepository.findBySeasonIdAndTeamId(season.getId(), team.getId()).orElseThrow();

        when(teamCardService.generateCard(any(SeasonTeam.class)))
                .thenThrow(new IOException("Playwright not available"));

        // when
        mockMvc.perform(post("/admin/tools/team-cards/generate/" + seasonTeam.getId()))
                // then
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tools/team-cards?seasonId=" + season.getId()))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void givenSeason_whenGenerateAllCards_thenRedirectsWithSuccess() throws Exception {
        // given
        var season = testHelper.createSeason("Test_TC_All Season");

        when(teamCardService.generateAllCards(any())).thenReturn(List.of("/a.png", "/b.png"));

        // when
        mockMvc.perform(post("/admin/tools/team-cards/generate-all")
                        .param("seasonId", season.getId().toString()))
                // then
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tools/team-cards?seasonId=" + season.getId()))
                .andExpect(flash().attributeExists("successMessage"));

        verify(teamCardService).generateAllCards(any());
    }

    @Test
    void givenExistingCardFile_whenDownloadCard_thenReturnsFileWithContentDisposition() throws Exception {
        // given
        var season = testHelper.createSeason("Test_TC_DL Season");
        var team = testHelper.createTeam("TC DL Team", "TDL");
        season.addTeam(team);
        var seasonTeam = seasonTeamRepository.findBySeasonIdAndTeamId(season.getId(), team.getId()).orElseThrow();

        // Create a fake card file
        String cardRelativePath = "team-cards/" + season.getId() + "/TDL.png";
        Path cardFile = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(cardRelativePath);
        Files.createDirectories(cardFile.getParent());
        Files.write(cardFile, new byte[]{(byte) 0x89, 'P', 'N', 'G'});

        when(teamCardService.getCardPath(any(SeasonTeam.class)))
                .thenReturn("/uploads/" + cardRelativePath);

        // when
        var result = mockMvc.perform(get("/admin/tools/team-cards/download/" + seasonTeam.getId()))
                // then
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader("Content-Disposition")).contains("TDL-card.png");

        // Cleanup
        Files.deleteIfExists(cardFile);
        Files.deleteIfExists(cardFile.getParent());
    }

    @Test
    void givenMissingCardFile_whenDownloadCard_thenReturnsNotFound() throws Exception {
        // given
        var season = testHelper.createSeason("Test_TC_404 Season");
        var team = testHelper.createTeam("TC 404 Team", "T404");
        season.addTeam(team);
        var seasonTeam = seasonTeamRepository.findBySeasonIdAndTeamId(season.getId(), team.getId()).orElseThrow();

        when(teamCardService.getCardPath(any(SeasonTeam.class)))
                .thenReturn("/uploads/team-cards/nonexistent/T404.png");

        // when
        mockMvc.perform(get("/admin/tools/team-cards/download/" + seasonTeam.getId()))
                // then
                .andExpect(status().isNotFound());
    }

    @Test
    void givenRuntimeException_whenGenerateCard_thenReturns3xxWithError() throws Exception {
        // given
        var season = testHelper.createSeason("Test_TC_REx Season");
        var team = testHelper.createTeam("TC REx Team", "TRX");
        season.addTeam(team);
        var seasonTeam = seasonTeamRepository.findBySeasonIdAndTeamId(season.getId(), team.getId()).orElseThrow();

        when(teamCardService.generateCard(any(SeasonTeam.class)))
                .thenThrow(new RuntimeException("Playwright failure"));

        // when
        mockMvc.perform(post("/admin/tools/team-cards/generate/" + seasonTeam.getId()))
                // then
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void givenRuntimeException_whenGenerateAllCards_thenReturns3xxWithError() throws Exception {
        // given
        var season = testHelper.createSeason("Test_TC_AllREx Season");

        when(teamCardService.generateAllCards(any()))
                .thenThrow(new RuntimeException("Playwright failure"));

        // when
        mockMvc.perform(post("/admin/tools/team-cards/generate-all")
                        .param("seasonId", season.getId().toString()))
                // then
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));
    }

    @Test
    void givenSeasonWithCards_whenDownloadAllAsZip_thenReturnsZipFile() throws Exception {
        // given
        var season = testHelper.createSeason("Test_TC_ZIP Season");
        var team = testHelper.createTeam("TC ZIP Team", "TZIP");
        season.addTeam(team);

        // Create a fake card file
        String cardRelativePath = "team-cards/" + season.getId() + "/TZIP.png";
        Path cardFile = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(cardRelativePath);
        Files.createDirectories(cardFile.getParent());
        Files.write(cardFile, new byte[]{1, 2, 3, 4});

        when(teamCardService.getCardPath(any(SeasonTeam.class)))
                .thenReturn("/uploads/" + cardRelativePath);

        // when
        var result = mockMvc.perform(get("/admin/tools/team-cards/download-all")
                        .param("seasonId", season.getId().toString()))
                // then
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader("Content-Disposition")).contains(".zip");
        assertThat(result.getResponse().getContentAsByteArray().length).isGreaterThan(0);

        // Cleanup
        Files.deleteIfExists(cardFile);
        Files.deleteIfExists(cardFile.getParent());
    }
}
