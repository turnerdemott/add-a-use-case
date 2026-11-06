package team.projectpulse.student;

import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import team.projectpulse.AbstractIntegrationTest;
import team.projectpulse.team.Team;
import team.projectpulse.team.TeamRepository;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * UC-STU-deactivate-student extension 6a when two confirmations race. Two requests in flight at once need two real
 * transactions, so this test opts out of the base class's rollback transaction, commits, and restores John in a
 * finally block. Each request takes the student's row lock; the second must wait, then see him deactivated.
 *
 * <p>Caveat on what this proves: it detects a missing lock only when the two requests overlap. With the
 * {@code @Lock} removed from {@code findByIdInLifecycleScopeForUpdate} it failed in 3 of 3 runs (two 200s), but a
 * run where the requests happen not to overlap would pass without the lock as well.
 */
@DisplayName("Integration test for two concurrent deactivations of one student")
class StudentDeactivationConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final int JOHN = 4;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    StudentRepository studentRepository;

    @Autowired
    TeamRepository teamRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Value("${api.endpoint.base-url}")
    String baseUrl;


    private String login(String email) throws Exception {
        MvcResult mvcResult = this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic(email, "123456"))).andReturn();
        JSONObject json = new JSONObject(mvcResult.getResponse().getContentAsString());
        return "Bearer " + json.getJSONObject("data").getString("token");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void testTwoConcurrentDeactivationsSucceedOnceAndRecordHisTeam() throws Exception {
        // Given: Bill and Wei both instruct John's course section; John is on Team1
        String billToken = login("b.gates@abc.edu");
        String weiToken = login("b.wei@abc.edu");
        Integer team1Id = this.transactionTemplate.execute(status -> this.studentRepository.findById(JOHN).orElseThrow().getTeam().getTeamId());
        CountDownLatch startTogether = new CountDownLatch(1);

        try {
            // When: both confirm at once
            CompletableFuture<Integer> bill = CompletableFuture.supplyAsync(() -> deactivateJohnAfter(startTogether, billToken));
            CompletableFuture<Integer> wei = CompletableFuture.supplyAsync(() -> deactivateJohnAfter(startTogether, weiToken));
            startTogether.countDown();

            // Then: one 200 and one 409, and his former team is Team1, not the "no team" a second write would record
            assertThat(List.of(bill.get(), wei.get())).containsExactlyInAnyOrder(200, 409);
            Integer formerTeamId = this.transactionTemplate.execute(status -> this.studentRepository.findById(JOHN).orElseThrow().getFormerTeam().getTeamId());
            assertThat(formerTeamId).isEqualTo(team1Id);
        } finally {
            // Restore the seed data that the other integration tests rely on
            this.transactionTemplate.executeWithoutResult(status -> {
                Student john = this.studentRepository.findById(JOHN).orElseThrow();
                Team team1 = this.teamRepository.findById(team1Id).orElseThrow();
                if (john.getTeam() == null) {
                    team1.addStudent(john);
                }
                john.setFormerTeam(null);
                john.setDeactivatedWeek(null);
                john.setEnabled(true);
            });
        }
    }

    private int deactivateJohnAfter(CountDownLatch startTogether, String token) {
        try {
            startTogether.await();
            return this.mockMvc.perform(patch(this.baseUrl + "/students/" + JOHN + "/deactivate").header(HttpHeaders.AUTHORIZATION, token))
                    .andReturn().getResponse().getStatus();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

}
