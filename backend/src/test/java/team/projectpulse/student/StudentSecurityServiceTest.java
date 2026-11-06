package team.projectpulse.student;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.projectpulse.course.Course;
import team.projectpulse.instructor.Instructor;
import team.projectpulse.section.Section;
import team.projectpulse.system.UserUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StudentSecurityServiceTest {

    @Mock
    StudentRepository studentRepository;
    @Mock
    UserUtils userUtils;

    @InjectMocks
    StudentSecurityService studentSecurityService;

    Course course;
    Student jana;

    @BeforeEach
    void setUp() {
        Instructor admin = new Instructor("b.wei@abc.edu", "Bingyang", "Wei", "b.wei@abc.edu", "123456", true, "admin instructor");
        admin.setId(1);
        this.course = new Course("COSC 40993 Senior Design", "Senior design");
        this.course.setCourseAdmin(admin);
        Section section = new Section("2023-2024", LocalDate.of(2023, 7, 31), LocalDate.of(2024, 10, 6), true, DayOfWeek.MONDAY, LocalTime.of(23, 59), DayOfWeek.TUESDAY, LocalTime.of(23, 59));
        section.setCourse(this.course);
        this.jana = new Student("j.norton@abc.edu", "Jana", "Norton", "j.norton@abc.edu", "123456", true, "student");
        this.jana.setId(20);
        section.addStudent(this.jana);
    }

    @Test
    void testIsCurrentUserAdminOfStudentCourseForTheCourseAdmin() {
        given(this.studentRepository.findById(20)).willReturn(Optional.of(this.jana));
        given(this.userUtils.getUserId()).willReturn(1);

        assertThat(this.studentSecurityService.isCurrentUserAdminOfStudentCourse(20)).isTrue();
    }

    @Test
    void testIsCurrentUserAdminOfStudentCourseForAnotherUser() {
        given(this.studentRepository.findById(20)).willReturn(Optional.of(this.jana));
        given(this.userUtils.getUserId()).willReturn(2);

        assertThat(this.studentSecurityService.isCurrentUserAdminOfStudentCourse(20)).isFalse();
    }

    @Test
    void testIsCurrentUserAdminOfStudentCourseForUnknownStudent() {
        given(this.studentRepository.findById(999)).willReturn(Optional.empty());

        assertThat(this.studentSecurityService.isCurrentUserAdminOfStudentCourse(999)).isFalse();
    }

}
