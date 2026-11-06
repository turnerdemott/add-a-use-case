package team.projectpulse.student;

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

}
