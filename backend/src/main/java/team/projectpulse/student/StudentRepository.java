package team.projectpulse.student;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Integer>, JpaSpecificationExecutor<Student> {

    // Find all students in a section
    List<Student> findBySectionSectionId(Integer sectionId);

    List<Student> findByTeamTeamId(Integer teamId);

    /**
     * The students removed from this team by deactivation who still count as its members for the given week, that
     * is, whose deactivation week is that week or a later one (BR-student-lifecycle).
     */
    List<Student> findByFormerTeamTeamIdAndDeactivatedWeekGreaterThanEqual(Integer teamId, String week);

    /**
     * Loads one student, locked for write, only if the caller may change her lifecycle: an instructor of her course
     * section, or the course admin who owns her course (BR-student-lifecycle). This is the second enforcement point
     * of the Authorization architecture on a flat route, so the scope comes from the caller, not the request.
     *
     * <p>The write lock serializes two deactivations of the same student: the second waits for the first to commit,
     * then reads her as already deactivated instead of overwriting the team and week the first recorded.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from Student s
            where s.id = :studentId
              and (exists (select i.id from Section sec join sec.instructors i
                           where sec = s.section and i.id = :callerId)
                   or exists (select sec.sectionId from Section sec
                              where sec = s.section and sec.course.courseAdmin.id = :callerId))
            """)
    Optional<Student> findByIdInLifecycleScopeForUpdate(@Param("studentId") Integer studentId, @Param("callerId") Integer callerId);

}
