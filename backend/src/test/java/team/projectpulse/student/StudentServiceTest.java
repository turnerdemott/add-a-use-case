package team.projectpulse.student;

import org.junit.jupiter.api.AfterEach;
import team.projectpulse.instructor.Instructor;
import team.projectpulse.section.Section;
import team.projectpulse.section.SectionRepository;
import team.projectpulse.system.UserUtils;
import team.projectpulse.system.exception.ObjectNotFoundException;
import team.projectpulse.team.Team;
import team.projectpulse.user.MyUserPrincipal;
import team.projectpulse.user.userinvitation.UserInvitationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    @Mock
    StudentRepository studentRepository;
    @Mock
    SectionRepository sectionRepository;
    @Mock
    UserInvitationService userInvitationService;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    UserUtils userUtils;
    @Mock
    Clock clock;

    @InjectMocks
    private StudentService studentService;

    List<Student> students;

    @BeforeEach
    void setUp() {
        // Create students
        Student john = new Student("john", "John", "Smith", "j.smith@abc.edu", "123456", true, "student");
        Student eric = new Student("eric", "Eric", "Hudson", "e.hudson@abc.edu", "123456", true, "student");
        Student jerry = new Student("jerry", "Jerry", "Moon", "j.moon@abc.edu", "123456", true, "student");
        Student woody = new Student("woody", "Woody", "Allen", "w.allen@abc.edu", "123456", true, "student");
        Student amanda = new Student("amanda", "Amanda", "Wagner", "a.wagner@abc.edu", "123456", true, "student");
        Student cora = new Student("cora", "Cora", "Manning", "c.manning@abc.edu", "123456", true, "student");
        Student agustin = new Student("agustin", "Agustin", "Freeman", "a.freeman@abc.edu", "123456", true, "student");
        Student mavis = new Student("mavis", "Mavis", "Huber", "m.huber@abc.edu", "123456", true, "student");
        Student mary = new Student("mary", "Mary", "Vargas", "m.vargas@abc.edu", "123456", true, "student");
        Student rosendo = new Student("rosendo", "Rosendo", "Maxwell", "r.maxwell@abc.edu", "123456", true, "student");

        this.students = List.of(john, eric, jerry, woody, amanda, cora, agustin, mavis, mary, rosendo);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext(); // Clear the security context after each test to avoid interference between tests
    }

    @Test
    void testFindByCriteria() {
        // Given
        Map<String, String> searchCriteria = Map.of("firstName", "john");
        PageImpl expectedPage = new PageImpl(List.of(this.students.get(0)));

        given(this.userUtils.getUserSectionId()).willReturn(2);
        given(this.studentRepository.findAll(any(Specification.class), any(Pageable.class))).willReturn(expectedPage);
        // When
        Page<Student> result = this.studentService.findByCriteria(searchCriteria, PageRequest.of(0, 20));

        // Then
        assertThat(result.getContent().size()).isEqualTo(1);
    }

    @Test
    void testFindStudentById() {
        // Given
        Student john = this.students.get(0);
        given(this.studentRepository.findById(1)).willReturn(Optional.of(john));

        // When
        Student result = this.studentService.findStudentById(1);

        // Then
        assertThat(result.getFirstName()).isEqualTo("John");
        assertThat(result.getLastName()).isEqualTo("Smith");
    }

    @Test
    void testSaveStudent() {
        // Given
        Student jan = new Student("jan", "Jan", "Mckinney", "j.mckinney@abc.edu", "Abc123456", true, "student");
        doNothing().when(this.userInvitationService).validateUserInvitation(anyString(), anyString(), anyInt(), anyInt(), anyString());
        given(this.sectionRepository.findById(1)).willReturn(Optional.of(new Section()));
        given(this.passwordEncoder.encode(anyString())).willReturn("Encoded Password");
        given(this.studentRepository.save(jan)).willReturn(jan);

        // When
        Student result = this.studentService.saveStudent(jan, 1, 1, "token", "student");

        // Then
        assertThat(result.getFirstName()).isEqualTo("Jan");
        assertThat(result.getLastName()).isEqualTo("Mckinney");
        assertThat(result.getEmail()).isEqualTo("j.mckinney@abc.edu");
        assertThat(result.getRoles()).isEqualTo("student");
        assertThat(result.getSection()).isNotNull();
        assertThat(result.isEnabled()).isEqualTo(true);
    }

    @Test
    void testStudentUpdateOwnInfo() {
        // Given
        Student update = new Student("john", "John (updated)", "Smith (updated)", "j.smith@abc.edu (updated)", "", false, "admin student");
        given(this.studentRepository.findById(1)).willReturn(Optional.of(this.students.get(0)));
        given(this.studentRepository.save(any(Student.class))).willReturn(this.students.get(0));

        Instructor instructor = new Instructor();
        instructor.setRoles("student");
        MyUserPrincipal myUserPrincipal = new MyUserPrincipal(instructor);

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken(myUserPrincipal, null, myUserPrincipal.getAuthorities()));
        SecurityContextHolder.setContext(securityContext);

        // When
        Student result = this.studentService.updateStudent(1, update);

        // Then
        assertThat(result.getUsername()).isEqualTo(update.getUsername());
        assertThat(result.getFirstName()).isEqualTo(update.getFirstName());
        assertThat(result.getLastName()).isEqualTo(update.getLastName());
        assertThat(result.getEmail()).isEqualTo(update.getEmail());
        assertThat(result.getRoles()).isNotEqualTo(update.getRoles());
        assertThat(result.isEnabled()).isNotEqualTo(update.isEnabled());
    }

    @Test
    void testInstructorUpdatesStudent() {
        // Given
        Student update = new Student("john", "John (updated)", "Smith (updated)", "j.smith@abc.edu (updated)", "", false, "admin student");
        given(this.studentRepository.findById(1)).willReturn(Optional.of(this.students.get(0)));
        given(this.studentRepository.save(any(Student.class))).willReturn(this.students.get(0));

        Instructor instructor = new Instructor();
        instructor.setRoles("instructor");
        MyUserPrincipal myUserPrincipal = new MyUserPrincipal(instructor);

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken(myUserPrincipal, null, myUserPrincipal.getAuthorities()));
        SecurityContextHolder.setContext(securityContext);

        // When
        Student result = this.studentService.updateStudent(1, update);

        // Then
        assertThat(result.getUsername()).isEqualTo(update.getUsername());
        assertThat(result.getFirstName()).isEqualTo(update.getFirstName());
        assertThat(result.getLastName()).isEqualTo(update.getLastName());
        assertThat(result.getEmail()).isEqualTo(update.getEmail());
//        assertThat(result.getRoles()).isEqualTo(update.getRoles());
        assertThat(result.isEnabled()).isEqualTo(update.isEnabled());
    }

    @Test
    void testDeleteStudent() {
        // Given
        given(this.studentRepository.findById(1)).willReturn(Optional.of(this.students.get(0)));
        doNothing().when(this.studentRepository).deleteById(1);

        // When
        this.studentService.deleteStudent(1);

        // Then
        verify(this.studentRepository, times(1)).deleteById(1);
    }

    // UC-STU-deactivate-student

    @Test
    void testDeactivatingAStudentRecordsHerTeamAndWeekAndRemovesHerFromIt() {
        // Given: Jana (id 20) is on Team1; the clock is at the dev date, Aug 20, 2023, which is in 2023-W33
        Team team1 = new Team("Team1", "Team 1 description", "https://www.team1.com");
        Student jana = new Student("jana", "Jana", "Norton", "j.norton@abc.edu", "123456", true, "student");
        jana.setId(20);
        team1.addStudent(jana);
        Clock fixedClock = Clock.fixed(LocalDate.of(2023, 8, 20).atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        given(this.clock.instant()).willReturn(fixedClock.instant());
        given(this.clock.getZone()).willReturn(fixedClock.getZone());
        given(this.userUtils.getUserId()).willReturn(2);
        given(this.studentRepository.findByIdInLifecycleScopeForUpdate(20, 2)).willReturn(Optional.of(jana));
        given(this.studentRepository.save(jana)).willReturn(jana);

        // When
        Student deactivated = this.studentService.deactivateStudent(20);

        // Then
        assertThat(deactivated.isEnabled()).isFalse();
        assertThat(deactivated.getTeam()).isNull();
        assertThat(deactivated.getFormerTeam()).isSameAs(team1);
        assertThat(deactivated.getDeactivatedWeek()).isEqualTo("2023-W33");
        assertThat(team1.getStudents()).doesNotContain(jana);
        verify(this.studentRepository, times(1)).save(jana);
    }

    @Test
    void testDeactivatingAStudentOnNoTeamRecordsTheWeekAndNoTeam() {
        // Given: Jana (id 20) is on no team; the clock is at the dev date, Aug 20, 2023, which is in 2023-W33
        Student jana = new Student("jana", "Jana", "Norton", "j.norton@abc.edu", "123456", true, "student");
        jana.setId(20);
        Clock fixedClock = Clock.fixed(LocalDate.of(2023, 8, 20).atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        given(this.clock.instant()).willReturn(fixedClock.instant());
        given(this.clock.getZone()).willReturn(fixedClock.getZone());
        given(this.userUtils.getUserId()).willReturn(2);
        given(this.studentRepository.findByIdInLifecycleScopeForUpdate(20, 2)).willReturn(Optional.of(jana));
        given(this.studentRepository.save(jana)).willReturn(jana);

        // When
        Student deactivated = this.studentService.deactivateStudent(20);

        // Then
        assertThat(deactivated.isEnabled()).isFalse();
        assertThat(deactivated.getFormerTeam()).isNull();
        assertThat(deactivated.getDeactivatedWeek()).isEqualTo("2023-W33");
    }

    @Test
    void testDeactivatingAnAlreadyDeactivatedStudentIsRefusedAndKeepsHerFirstRecord() {
        // Given: Jana (id 20) was deactivated off Team1 in 2023-W31
        Team team1 = new Team("Team1", "Team 1 description", "https://www.team1.com");
        Student jana = new Student("jana", "Jana", "Norton", "j.norton@abc.edu", "123456", true, "student");
        jana.setId(20);
        jana.setEnabled(false);
        jana.setFormerTeam(team1);
        jana.setDeactivatedWeek("2023-W31");
        given(this.userUtils.getUserId()).willReturn(2);
        given(this.studentRepository.findByIdInLifecycleScopeForUpdate(20, 2)).willReturn(Optional.of(jana));

        // When
        Throwable thrown = catchThrowable(() -> this.studentService.deactivateStudent(20));

        // Then
        assertThat(thrown).isInstanceOf(StudentAlreadyDeactivatedException.class)
                .hasMessage("Jana Norton is already deactivated.");
        assertThat(jana.getFormerTeam()).isSameAs(team1);
        assertThat(jana.getDeactivatedWeek()).isEqualTo("2023-W31");
        verify(this.studentRepository, never()).save(any(Student.class));
    }

    @Test
    void testDeactivatingAStudentTheScopedFinderDoesNotReturnIsNotFoundAndSavesNothing() {
        // Given: the finder returns nothing (who it returns is StudentRepositoryIntegrationTest's concern, not this one's)
        given(this.userUtils.getUserId()).willReturn(3);
        given(this.studentRepository.findByIdInLifecycleScopeForUpdate(20, 3)).willReturn(Optional.empty());

        // When
        Throwable thrown = catchThrowable(() -> this.studentService.deactivateStudent(20));

        // Then
        assertThat(thrown).isInstanceOf(ObjectNotFoundException.class);
        verify(this.studentRepository, never()).save(any(Student.class));
    }

    @Test
    void testTeamListingForAWeekListsEachFormerMemberOnceAfterTheCurrentMembers() {
        // Given: Jana (20) is on Team1 now; Lucius (21) was deactivated off it. The week filter is the derived query's
        // job, so it is stubbed here; this test covers merging and de-duplicating the two lists
        Team team1 = new Team("Team1", "Team 1 description", "https://www.team1.com");
        Student jana = new Student("jana", "Jana", "Norton", "j.norton@abc.edu", "123456", true, "student");
        jana.setId(20);
        team1.addStudent(jana);
        Student lucius = new Student("lucius", "Lucius", "Osborne", "l.osborne@abc.edu", "123456", false, "student");
        lucius.setId(21);
        given(this.studentRepository.findByTeamTeamId(1)).willReturn(List.of(jana));
        // Jana also comes back from the former-member query, as a student reactivated onto her old team would
        given(this.studentRepository.findByFormerTeamTeamIdAndDeactivatedWeekGreaterThanEqual(1, "2023-W32")).willReturn(List.of(lucius, jana));

        // When
        List<Student> listed = this.studentService.findStudentsByTeamId(1, "2023-W32");

        // Then
        assertThat(listed).containsExactly(jana, lucius);
    }

    @Test
    void testTeamListingWithoutAWeekListsCurrentMembersOnly() {
        // Given
        Team team1 = new Team("Team1", "Team 1 description", "https://www.team1.com");
        Student jana = new Student("jana", "Jana", "Norton", "j.norton@abc.edu", "123456", true, "student");
        team1.addStudent(jana);
        given(this.studentRepository.findByTeamTeamId(1)).willReturn(List.of(jana));

        // When
        List<Student> listed = this.studentService.findStudentsByTeamId(1, null);

        // Then
        assertThat(listed).containsExactly(jana);
        verify(this.studentRepository, never()).findByFormerTeamTeamIdAndDeactivatedWeekGreaterThanEqual(any(), any());
    }

    @Test
    void testTeamListingRefusesAMalformedWeek() {
        // When
        Throwable thrown = catchThrowable(() -> this.studentService.findStudentsByTeamId(1, "week 32"));

        // Then
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

}
