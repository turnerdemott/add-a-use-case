package team.projectpulse.security.authorizationmanagers;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import team.projectpulse.student.StudentSecurityService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Who may deactivate a student (BR-student-lifecycle): an instructor of her course section, or the course admin who
 * owns her course. The two questions themselves are StudentSecurityService's; this covers how the manager combines
 * them and how it treats a path variable it cannot read.
 */
@ExtendWith(MockitoExtension.class)
class StudentLifecycleAuthorizationManagerTest {

    @Mock
    StudentSecurityService studentSecurityService;

    @InjectMocks
    StudentLifecycleAuthorizationManager studentLifecycleAuthorizationManager;


    @Test
    @DisplayName("Grants an instructor of her course section")
    void grantsAnInstructorOfHerCourseSection() {
        given(this.studentSecurityService.isCurrentUserInstructorOfStudentSection(4)).willReturn(true);
        RequestAuthorizationContext context = new RequestAuthorizationContext(mock(HttpServletRequest.class), Map.of("studentId", "4"));

        AuthorizationResult result = this.studentLifecycleAuthorizationManager.authorize(() -> null, context);

        assertThat(result.isGranted()).isTrue();
    }

    @Test
    @DisplayName("Grants the course admin who owns her course but does not instruct her course section")
    void grantsTheOwnerOfHerCourse() {
        given(this.studentSecurityService.isCurrentUserInstructorOfStudentSection(4)).willReturn(false);
        given(this.studentSecurityService.isCurrentUserAdminOfStudentCourse(4)).willReturn(true);
        RequestAuthorizationContext context = new RequestAuthorizationContext(mock(HttpServletRequest.class), Map.of("studentId", "4"));

        AuthorizationResult result = this.studentLifecycleAuthorizationManager.authorize(() -> null, context);

        assertThat(result.isGranted()).isTrue();
    }

    @Test
    @DisplayName("Denies a caller who neither instructs her course section nor owns her course")
    void deniesEveryoneElse() {
        given(this.studentSecurityService.isCurrentUserInstructorOfStudentSection(4)).willReturn(false);
        given(this.studentSecurityService.isCurrentUserAdminOfStudentCourse(4)).willReturn(false);
        RequestAuthorizationContext context = new RequestAuthorizationContext(mock(HttpServletRequest.class), Map.of("studentId", "4"));

        AuthorizationResult result = this.studentLifecycleAuthorizationManager.authorize(() -> null, context);

        assertThat(result.isGranted()).isFalse();
    }

    @Test
    @DisplayName("Denies when the bound studentId is not a number, without asking about any student")
    void deniesAStudentIdThatIsNotANumber() {
        RequestAuthorizationContext context = new RequestAuthorizationContext(mock(HttpServletRequest.class), Map.of("studentId", "not-a-number"));

        AuthorizationResult result = this.studentLifecycleAuthorizationManager.authorize(() -> null, context);

        assertThat(result.isGranted()).isFalse();
        verifyNoInteractions(this.studentSecurityService);
    }

}
