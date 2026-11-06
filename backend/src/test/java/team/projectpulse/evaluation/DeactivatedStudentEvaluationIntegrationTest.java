package team.projectpulse.evaluation;

import jakarta.persistence.EntityManager;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import team.projectpulse.AbstractIntegrationTest;
import team.projectpulse.evaluation.dto.PeerEvaluationDto;
import team.projectpulse.evaluation.dto.RatingDto;
import team.projectpulse.notification.SubmissionStatusService;
import team.projectpulse.notification.SubmittableItem;
import team.projectpulse.student.Student;
import team.projectpulse.system.StatusCode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-STU-deactivate-student's week clause (BR-student-lifecycle) through the endpoints that apply it. Each test has
 * Bill deactivate John (id 4) off Team1 first. The dev clock is Aug 20, 2023, so John's deactivation week is
 * 2023-W33 and the week open for peer evaluation is 2023-W32. Eric (id 5) and Jerry stay on Team1; the course
 * section is 2023-2024 (id 2).
 */
@DisplayName("Integration tests for evaluations and reports of a deactivated student")
class DeactivatedStudentEvaluationIntegrationTest extends AbstractIntegrationTest {

    private static final int JOHN = 4;
    private static final int ERIC = 5;
    private static final int SECTION = 2;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JsonMapper jsonMapper;

    @Autowired
    EntityManager entityManager;

    @Autowired
    SubmissionStatusService submissionStatusService;

    @Value("${api.endpoint.base-url}")
    String baseUrl;


    private String login(String email) throws Exception {
        MvcResult mvcResult = this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic(email, "123456"))).andReturn();
        JSONObject json = new JSONObject(mvcResult.getResponse().getContentAsString());
        return "Bearer " + json.getJSONObject("data").getString("token");
    }

    // The evaluation form's list (GET /students/teams/{teamId}?week=)

    @Test
    void testTeamListingForAWeekListsHimUpToHisDeactivationWeekOnly() throws Exception {
        // Given
        String billToken = login("b.gates@abc.edu");
        String ericToken = login("e.hudson@abc.edu");
        Integer team1Id = this.entityManager.find(Student.class, JOHN).getTeam().getTeamId();
        this.mockMvc.perform(patch(this.baseUrl + "/students/" + JOHN + "/deactivate").header(HttpHeaders.AUTHORIZATION, billToken))
                .andExpect(status().isOk());

        // When, then
        this.mockMvc.perform(get(this.baseUrl + "/students/teams/" + team1Id).param("week", "2023-W33").header(HttpHeaders.AUTHORIZATION, ericToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem(JOHN)));
        this.mockMvc.perform(get(this.baseUrl + "/students/teams/" + team1Id).param("week", "2023-W34").header(HttpHeaders.AUTHORIZATION, ericToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", not(hasItem(JOHN))));
    }

    @Test
    void testTeamListingWithoutAWeekListsCurrentMembersOnly() throws Exception {
        // Given
        String billToken = login("b.gates@abc.edu");
        String ericToken = login("e.hudson@abc.edu");
        Integer team1Id = this.entityManager.find(Student.class, JOHN).getTeam().getTeamId();
        this.mockMvc.perform(patch(this.baseUrl + "/students/" + JOHN + "/deactivate").header(HttpHeaders.AUTHORIZATION, billToken))
                .andExpect(status().isOk());

        // When, then
        this.mockMvc.perform(get(this.baseUrl + "/students/teams/" + team1Id).header(HttpHeaders.AUTHORIZATION, ericToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem(ERIC)))
                .andExpect(jsonPath("$.data[*].id", not(hasItem(JOHN))));
    }

    @Test
    void testTeamListingRefusesAMalformedWeek() throws Exception {
        String ericToken = login("e.hudson@abc.edu");
        Integer team1Id = this.entityManager.find(Student.class, ERIC).getTeam().getTeamId();

        this.mockMvc.perform(get(this.baseUrl + "/students/teams/" + team1Id).param("week", "week 33").header(HttpHeaders.AUTHORIZATION, ericToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(StatusCode.INVALID_ARGUMENT));
    }

    // Submitting an evaluation of him (POST /evaluations)

    @Test
    void testFormerTeammateSubmitsAnEvaluationOfHimForTheOpenWeek() throws Exception {
        // Given: Eric has not yet evaluated John for 2023-W32; then John is deactivated (in 2023-W33)
        this.entityManager.createQuery("delete from Rating r where r.peerEvaluation.evaluator.id = 5 and r.peerEvaluation.evaluatee.id = 4 and r.peerEvaluation.week = '2023-W32'").executeUpdate();
        this.entityManager.createQuery("delete from PeerEvaluation e where e.evaluator.id = 5 and e.evaluatee.id = 4 and e.week = '2023-W32'").executeUpdate();
        String billToken = login("b.gates@abc.edu");
        String ericToken = login("e.hudson@abc.edu");
        this.mockMvc.perform(patch(this.baseUrl + "/students/" + JOHN + "/deactivate").header(HttpHeaders.AUTHORIZATION, billToken))
                .andExpect(status().isOk());
        List<RatingDto> ratings = List.of(
                new RatingDto(null, 1, 8.0),
                new RatingDto(null, 2, 8.0),
                new RatingDto(null, 3, 8.0),
                new RatingDto(null, 4, 8.0),
                new RatingDto(null, 5, 8.0),
                new RatingDto(null, 6, 8.0));
        PeerEvaluationDto ericOfJohn = new PeerEvaluationDto(null, "2023-W32", ERIC, "Eric Hudson", JOHN, "John Smith", ratings, 48.0, "Thanks for the work", "Left mid-term", null, null);

        // When, then
        this.mockMvc.perform(post(this.baseUrl + "/evaluations").contentType(MediaType.APPLICATION_JSON).content(this.jsonMapper.writeValueAsString(ericOfJohn)).header(HttpHeaders.AUTHORIZATION, ericToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.data.evaluateeId").value(JOHN))
                .andExpect(jsonPath("$.data.week").value("2023-W32"));
    }

    // The section's peer evaluation report

    @Test
    void testSectionReportListsHimUnderTeam1UpToHisDeactivationWeekOnly() throws Exception {
        // Given
        String billToken = login("b.gates@abc.edu");
        this.mockMvc.perform(patch(this.baseUrl + "/students/" + JOHN + "/deactivate").header(HttpHeaders.AUTHORIZATION, billToken))
                .andExpect(status().isOk());

        // When, then: listed under Team1 for his deactivation week
        this.mockMvc.perform(get(this.baseUrl + "/evaluations/sections/" + SECTION + "/week/2023-W33").header(HttpHeaders.AUTHORIZATION, billToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.peerEvaluationAverages[?(@.studentId == 4)].teamName").value("Team1"));

        // When, then: absent for the week after
        this.mockMvc.perform(get(this.baseUrl + "/evaluations/sections/" + SECTION + "/week/2023-W34").header(HttpHeaders.AUTHORIZATION, billToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.peerEvaluationAverages[*].studentId", not(hasItem(JOHN))));
    }

    // His own per-student report

    @Test
    void testHisOwnReportStopsAtHisDeactivationWeek() throws Exception {
        // Given
        String billToken = login("b.gates@abc.edu");
        this.mockMvc.perform(patch(this.baseUrl + "/students/" + JOHN + "/deactivate").header(HttpHeaders.AUTHORIZATION, billToken))
                .andExpect(status().isOk());

        // When, then: asked for W31 to W35, it returns W31 to W33
        this.mockMvc.perform(get(this.baseUrl + "/evaluations/students/" + JOHN).param("startWeek", "2023-W31").param("endWeek", "2023-W35").header(HttpHeaders.AUTHORIZATION, billToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].week").value(org.hamcrest.Matchers.contains("2023-W31", "2023-W32", "2023-W33")));
    }

    // BR-submission-owed after his removal from Team1

    @Test
    void testNobodyOwesAnEvaluationOfHimAndHeOwesNothing() throws Exception {
        // Given: nobody evaluated John for 2023-W32, and he evaluated nobody; then he is deactivated
        this.entityManager.createQuery("delete from Rating r where r.peerEvaluation.evaluatee.id = 4 or r.peerEvaluation.evaluator.id = 4").executeUpdate();
        this.entityManager.createQuery("delete from PeerEvaluation e where e.evaluatee.id = 4 or e.evaluator.id = 4").executeUpdate();
        String billToken = login("b.gates@abc.edu");
        this.mockMvc.perform(patch(this.baseUrl + "/students/" + JOHN + "/deactivate").header(HttpHeaders.AUTHORIZATION, billToken))
                .andExpect(status().isOk());
        this.entityManager.flush();
        this.entityManager.clear();

        // When
        List<Student> owing = this.submissionStatusService.findOwing(SECTION, SubmittableItem.PEER_EVALUATION, "2023-W32");

        // Then: Eric and Jerry evaluated every current member, so neither owes; John owes nothing
        assertThat(owing).extracting(Student::getEmail)
                .doesNotContain("j.smith@abc.edu", "e.hudson@abc.edu", "j.moon@abc.edu");
    }

}
