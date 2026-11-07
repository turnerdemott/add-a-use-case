package team.projectpulse.student;

import team.projectpulse.section.Section;
import team.projectpulse.section.SectionRepository;
import team.projectpulse.system.UserUtils;
import team.projectpulse.system.WeekKeys;
import team.projectpulse.team.Team;
import team.projectpulse.system.exception.ObjectNotFoundException;
import team.projectpulse.user.userinvitation.UserInvitationService;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class StudentService {

    private final StudentRepository studentRepository;
    private final SectionRepository sectionRepository;
    private final UserInvitationService userInvitationService;
    private final PasswordEncoder passwordEncoder;
    private final UserUtils userUtils;
    private final Clock clock;


    public StudentService(StudentRepository studentRepository, SectionRepository sectionRepository, UserInvitationService userInvitationService, PasswordEncoder passwordEncoder, UserUtils userUtils, Clock clock) {
        this.studentRepository = studentRepository;
        this.sectionRepository = sectionRepository;
        this.userInvitationService = userInvitationService;
        this.passwordEncoder = passwordEncoder;
        this.userUtils = userUtils;
        this.clock = clock;
    }

    public Page<Student> findByCriteria(Map<String, String> searchCriteria, Pageable pageable) {
        Integer sectionId = this.userUtils.getUserSectionId();

        Specification<Student> spec = Specification.unrestricted(); // Start with an unrestricted specification, matching all objects.

        if (StringUtils.hasLength(searchCriteria.get("firstName"))) {
            spec = spec.and(StudentSpecs.containsFirstName(searchCriteria.get("firstName")));
        }

        if (StringUtils.hasLength(searchCriteria.get("lastName"))) {
            spec = spec.and(StudentSpecs.containsLastName(searchCriteria.get("lastName")));
        }

        if (StringUtils.hasLength(searchCriteria.get("email"))) {
            spec = spec.and(StudentSpecs.hasEmail(searchCriteria.get("email")));
        }

        if (StringUtils.hasLength(searchCriteria.get("teamName"))) {
            spec = spec.and(StudentSpecs.hasTeamName(searchCriteria.get("teamName")));
        }

        if (StringUtils.hasLength(searchCriteria.get("teamId"))) {
            spec = spec.and(StudentSpecs.hasTeamId(searchCriteria.get("teamId")));
        }

        if (StringUtils.hasLength(searchCriteria.get("sectionName"))) {
            spec = spec.and(StudentSpecs.hasSectionName(searchCriteria.get("sectionName")));
        }

        spec = spec.and(StudentSpecs.belongsToSection(sectionId)); // Only show students that belong to the default section of the instructor

        return this.studentRepository.findAll(spec, pageable);
    }

    public Student findStudentById(Integer studentId) {
        return this.studentRepository.findById(studentId)
                .orElseThrow(() -> new ObjectNotFoundException("student", studentId));
    }

    /**
     * The team's current members and, when a week is given, also the students deactivated off this team who still
     * count as its members for that week: those whose deactivation week is that week or later (BR-student-lifecycle).
     * The peer evaluation form lists these so former teammates may evaluate them for that week.
     *
     * @param week a week key such as {@code "2023-W32"}, or null for the current members only
     * @throws IllegalArgumentException if the week is not a well-formed week key
     */
    public List<Student> findStudentsByTeamId(Integer teamId, String week) {
        List<Student> members = this.studentRepository.findByTeamTeamId(teamId);
        if (week == null) {
            return members;
        }
        WeekKeys.requireValid(week);
        Set<Integer> memberIds = members.stream().map(Student::getId).collect(Collectors.toSet());
        List<Student> students = new ArrayList<>(members);
        this.studentRepository.findByFormerTeamTeamIdAndDeactivatedWeekGreaterThanEqual(teamId, week).stream()
                .filter(formerMember -> !memberIds.contains(formerMember.getId())) // Reactivated onto the same team
                .forEach(students::add);
        return students;
    }

    public Student saveStudent(Student newStudent, Integer courseId, Integer sectionId, String registrationToken, String role) {
        // Validate user invitation
        this.userInvitationService.validateUserInvitation(newStudent.getEmail(), registrationToken, courseId, sectionId, role);

        // Password rule: At least 6 characters, contains at least one letter and one number.
        if (!newStudent.getPassword().matches("^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{6,}$")) {
            throw new IllegalArgumentException("Password must contain at least one letter and one number, and at least 6 characters.");
        }

        // Add the student to the section
        Section section = this.sectionRepository.findById(sectionId)
                .orElseThrow(() -> new ObjectNotFoundException("section", sectionId));
        section.addStudent(newStudent);

        newStudent.setEnabled(true);
        newStudent.setRoles("student");
        newStudent.setPassword(this.passwordEncoder.encode(newStudent.getPassword()));

        Student savedStudent = this.studentRepository.save(newStudent);
        // Delete the user invitation after the student is saved.
        this.userInvitationService.deleteUserInvitation(newStudent.getEmail());
        return savedStudent;
    }

    public Student updateStudent(Integer studentId, Student update) {
        Student oldStudent = this.studentRepository.findById(studentId)
                .orElseThrow(() -> new ObjectNotFoundException("student", studentId));

        // If the user is not an admin, then the user can only update her username, first rubricName, last rubricName, and email
        oldStudent.setUsername(update.getUsername());
        oldStudent.setFirstName(update.getFirstName());
        oldStudent.setLastName(update.getLastName());
        oldStudent.setEmail(update.getEmail());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // If the user is an admin, then the user can update all the fields
        if (authentication.getAuthorities().stream().anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_instructor"))) {
            oldStudent.setEnabled(update.isEnabled());
//            oldStudent.setRoles(update.getRoles());
        }

        return this.studentRepository.save(oldStudent);
    }

    /**
     * Deactivates a student (UC-STU-deactivate-student): disables her account, records the team she is on and the
     * week of her deactivation, and removes her from that team. Her other records are unchanged.
     *
     * <p>She is loaded only if the caller is an instructor of her course section or the course admin who owns her
     * course, and locked for write, so a concurrent second deactivation waits and then finds her already
     * deactivated.
     *
     * @throws ObjectNotFoundException             if she is not in the caller's scope (the route rule refuses that
     *                                             first, so this is reached only if she was deleted meanwhile)
     * @throws StudentAlreadyDeactivatedException if her account is already deactivated (extension 6a)
     */
    public Student deactivateStudent(Integer studentId) {
        Student student = this.studentRepository.findByIdInLifecycleScopeForUpdate(studentId, this.userUtils.getUserId())
                .orElseThrow(() -> new ObjectNotFoundException("student", studentId));

        // Tests enabled, not deactivatedWeek, so an account disabled through the generic edit (OI-65) is refused too
        if (!student.isEnabled()) {
            throw new StudentAlreadyDeactivatedException(student);
        }

        Team team = student.getTeam(); // Null when she is on no team (extension 6b): no team is recorded
        student.setFormerTeam(team);
        student.setDeactivatedWeek(WeekKeys.of(LocalDate.now(this.clock)));
        if (team != null) {
            team.removeStudent(student);
        }
        student.setEnabled(false);
        return this.studentRepository.save(student);
    }

    public void deleteStudent(Integer studentId) {
        this.studentRepository.findById(studentId)
                .orElseThrow(() -> new ObjectNotFoundException("student", studentId));
        this.studentRepository.deleteById(studentId);
    }

}
