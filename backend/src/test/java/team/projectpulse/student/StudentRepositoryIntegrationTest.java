package team.projectpulse.student;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import team.projectpulse.AbstractIntegrationTest;
import team.projectpulse.section.Section;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The caller-scoped finder behind UC-STU-deactivate-student: the second enforcement point of Authorization on a flat
 * route. Seed data: John (id 4) is in course section 2023-2024 of COSC 40993, which Bingyang Wei (id 1) owns and
 * instructs and Bill Gates (id 2) instructs; Tim Cook (id 3) owns another course and instructs none of its sections.
 */
@DisplayName("Integration tests for StudentRepository's caller-scoped finder")
class StudentRepositoryIntegrationTest extends AbstractIntegrationTest {

    private static final int JOHN = 4;
    private static final int WEI = 1;
    private static final int BILL = 2;
    private static final int TIM = 3;

    @Autowired
    StudentRepository studentRepository;

    @Autowired
    EntityManager entityManager;

    @Test
    void testFindsStudentForInstructorOfHerCourseSection() {
        assertThat(this.studentRepository.findByIdInLifecycleScopeForUpdate(JOHN, BILL))
                .hasValueSatisfying(student -> assertThat(student.getId()).isEqualTo(JOHN));
    }

    @Test
    void testFindsStudentForCourseAdminNotAssignedToHerCourseSection() {
        // Given: Wei owns John's course but no longer instructs his course section
        Section section = this.studentRepository.findById(JOHN).orElseThrow().getSection();
        section.getInstructors().removeIf(instructor -> instructor.getId().equals(WEI));
        this.entityManager.flush();
        this.entityManager.clear();

        // When, then
        assertThat(this.studentRepository.findByIdInLifecycleScopeForUpdate(JOHN, WEI))
                .hasValueSatisfying(student -> assertThat(student.getId()).isEqualTo(JOHN));
    }

    @Test
    void testFindsNothingForInstructorOfAnotherCourseSection() {
        assertThat(this.studentRepository.findByIdInLifecycleScopeForUpdate(JOHN, TIM)).isEmpty();
    }

    @Test
    void testFindsNothingForStudentHerself() {
        assertThat(this.studentRepository.findByIdInLifecycleScopeForUpdate(JOHN, JOHN)).isEmpty();
    }

    @Test
    void testFindsNothingForIdThatNamesNoStudent() {
        assertThat(this.studentRepository.findByIdInLifecycleScopeForUpdate(999, BILL)).isEmpty();
    }

}
