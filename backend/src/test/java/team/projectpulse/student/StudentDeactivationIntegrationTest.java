package team.projectpulse.student;

import jakarta.persistence.EntityManager;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import team.projectpulse.AbstractIntegrationTest;
import team.projectpulse.system.StatusCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-STU-deactivate-student through {@code PATCH /students/{studentId}/deactivate}. Seed data: John (id 4) is on
 * Team1 in course section 2023-2024 of COSC 40993, which Bingyang Wei owns and instructs and Bill Gates instructs.
 * Tim Cook owns another course and instructs none of its sections. Woody is on Team2.
 */
@DisplayName("Integration tests for deactivating a student")
class StudentDeactivationIntegrationTest extends AbstractIntegrationTest {

    private static final int JOHN = 4;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    StudentRepository studentRepository;

    @Autowired
    EntityManager entityManager;

    @Value("${api.endpoint.base-url}")
    String baseUrl;


    private String login(String email) throws Exception {
        MvcResult mvcResult = this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic(email, "123456"))).andReturn();
        JSONObject json = new JSONObject(mvcResult.getResponse().getContentAsString());
        return "Bearer " + json.getJSONObject("data").getString("token");
    }

    // *a: the caller is neither the course admin who owns her course nor an instructor of her course section

    @Test
    void testInstructorOfAnotherCourseSectionIsRefusedAndHeIsUnchanged() throws Exception {
        String timToken = login("t.cook@abc.edu");

        this.mockMvc.perform(patch(this.baseUrl + "/students/" + JOHN + "/deactivate").header(HttpHeaders.AUTHORIZATION, timToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN));

        Student john = this.studentRepository.findById(JOHN).orElseThrow();
        assertThat(john.isEnabled()).isTrue();
        assertThat(john.getTeam().getTeamName()).isEqualTo("Team1");
        assertThat(john.getDeactivatedWeek()).isNull();
    }

    @Test
    void testStudentHerselfIsRefused() throws Exception {
        String johnToken = login("j.smith@abc.edu");

        this.mockMvc.perform(patch(this.baseUrl + "/students/" + JOHN + "/deactivate").header(HttpHeaders.AUTHORIZATION, johnToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN));
    }

    @Test
    void testStudentOfAnotherTeamIsRefused() throws Exception {
        String woodyToken = login("w.allen@abc.edu");

        this.mockMvc.perform(patch(this.baseUrl + "/students/" + JOHN + "/deactivate").header(HttpHeaders.AUTHORIZATION, woodyToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN));
    }

    @Test
    void testIdThatNamesNoStudentIsRefusedNotNotFound() throws Exception {
        String billToken = login("b.gates@abc.edu");

        this.mockMvc.perform(patch(this.baseUrl + "/students/999999/deactivate").header(HttpHeaders.AUTHORIZATION, billToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN));
    }

    // Main success scenario (steps 5-6) and its postconditions

    @Test
    void testInstructorOfHerCourseSectionDeactivatesHer() throws Exception {
        // Given: John's records before
        String billToken = login("b.gates@abc.edu");
        Long activitiesBefore = this.entityManager.createQuery("select count(a) from Activity a where a.student.id = 4", Long.class).getSingleResult();
        Long evaluationsByHimBefore = this.entityManager.createQuery("select count(e) from PeerEvaluation e where e.evaluator.id = 4", Long.class).getSingleResult();
        Long evaluationsOfHimBefore = this.entityManager.createQuery("select count(e) from PeerEvaluation e where e.evaluatee.id = 4", Long.class).getSingleResult();

        // When, then (step 6): deactivated and off his team in the response
        this.mockMvc.perform(patch(this.baseUrl + "/students/" + JOHN + "/deactivate").header(HttpHeaders.AUTHORIZATION, billToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.code").value(StatusCode.SUCCESS))
                .andExpect(jsonPath("$.message").value("Deactivate student successfully"))
                .andExpect(jsonPath("$.data.id").value(JOHN))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.teamId").doesNotExist());

        // Then (POST-1, POST-2): recorded team and week; the dev clock is Aug 20, 2023, in 2023-W33
        this.entityManager.flush();
        this.entityManager.clear();
        Student john = this.studentRepository.findById(JOHN).orElseThrow();
        assertThat(john.isEnabled()).isFalse();
        assertThat(john.getTeam()).isNull();
        assertThat(john.getFormerTeam().getTeamName()).isEqualTo("Team1");
        assertThat(john.getDeactivatedWeek()).isEqualTo("2023-W33");
        assertThat(this.studentRepository.findByTeamTeamId(john.getFormerTeam().getTeamId()))
                .extracting(Student::getId)
                .doesNotContain(JOHN);

        // Then (POST-3): his activities and the evaluations by and of him are unchanged
        Long activitiesAfter = this.entityManager.createQuery("select count(a) from Activity a where a.student.id = 4", Long.class).getSingleResult();
        Long evaluationsByHimAfter = this.entityManager.createQuery("select count(e) from PeerEvaluation e where e.evaluator.id = 4", Long.class).getSingleResult();
        Long evaluationsOfHimAfter = this.entityManager.createQuery("select count(e) from PeerEvaluation e where e.evaluatee.id = 4", Long.class).getSingleResult();
        assertThat(activitiesAfter).isEqualTo(activitiesBefore);
        assertThat(evaluationsByHimAfter).isEqualTo(evaluationsByHimBefore);
        assertThat(evaluationsOfHimAfter).isEqualTo(evaluationsOfHimBefore);
    }

    @Test
    void testOwnerOfHerCourseNotAssignedToHerCourseSectionDeactivatesHer() throws Exception {
        // Given: Wei owns John's course but no longer instructs his course section
        String weiToken = login("b.wei@abc.edu");
        this.studentRepository.findById(JOHN).orElseThrow().getSection().getInstructors()
                .removeIf(instructor -> instructor.getUsername().equals("b.wei@abc.edu"));
        this.entityManager.flush();
        this.entityManager.clear();

        // When, then
        this.mockMvc.perform(patch(this.baseUrl + "/students/" + JOHN + "/deactivate").header(HttpHeaders.AUTHORIZATION, weiToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    // 6a: already deactivated

    @Test
    void testDeactivatingHimAgainIsRefusedAndKeepsHisFirstRecord() throws Exception {
        // Given: Bill already deactivated John
        String billToken = login("b.gates@abc.edu");
        this.mockMvc.perform(patch(this.baseUrl + "/students/" + JOHN + "/deactivate").header(HttpHeaders.AUTHORIZATION, billToken))
                .andExpect(status().isOk());

        // When, then
        this.mockMvc.perform(patch(this.baseUrl + "/students/" + JOHN + "/deactivate").header(HttpHeaders.AUTHORIZATION, billToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.CONFLICT))
                .andExpect(jsonPath("$.message").value("John Smith is already deactivated."));

        this.entityManager.flush();
        this.entityManager.clear();
        Student john = this.studentRepository.findById(JOHN).orElseThrow();
        assertThat(john.getFormerTeam().getTeamName()).isEqualTo("Team1");
        assertThat(john.getDeactivatedWeek()).isEqualTo("2023-W33");
    }

    // 6b: on no team

    @Test
    void testStudentOnNoTeamIsDeactivatedWithTheWeekAndNoTeam() throws Exception {
        // Given: Tracy is in the course section but on no team
        String billToken = login("b.gates@abc.edu");
        Student tracy = this.entityManager.createQuery("select s from Student s where s.email = 't.nicholson@abc.edu'", Student.class).getSingleResult();

        // When, then
        this.mockMvc.perform(patch(this.baseUrl + "/students/" + tracy.getId() + "/deactivate").header(HttpHeaders.AUTHORIZATION, billToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        this.entityManager.flush();
        this.entityManager.clear();
        Student deactivated = this.studentRepository.findById(tracy.getId()).orElseThrow();
        assertThat(deactivated.getDeactivatedWeek()).isEqualTo("2023-W33");
        assertThat(deactivated.getFormerTeam()).isNull();
    }

    // Access (FR-SEC-active-account)

    @Test
    void testDeactivatedStudentCannotLogInButAnEarlierTokenStillWorksUntilItExpires() throws Exception {
        // Given: John logged in before Bill deactivated him
        String johnToken = login("j.smith@abc.edu");
        String billToken = login("b.gates@abc.edu");
        this.mockMvc.perform(patch(this.baseUrl + "/students/" + JOHN + "/deactivate").header(HttpHeaders.AUTHORIZATION, billToken))
                .andExpect(status().isOk());

        // When, then: a new login is refused
        this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic("j.smith@abc.edu", "123456")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED));

        // When, then: the token issued before deactivation still authorizes his own record (the accepted window)
        this.mockMvc.perform(get(this.baseUrl + "/students/" + JOHN).header(HttpHeaders.AUTHORIZATION, johnToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

}
