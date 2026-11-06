package team.projectpulse.student;

/**
 * Refuses deactivating a student whose account is already deactivated (UC-STU-deactivate-student, extension 6a).
 * {@code ExceptionHandlerAdvice} maps it to HTTP {@code 409}.
 *
 * <p>Refused rather than accepted idempotently, so a second deactivation cannot overwrite the team and week her
 * first one recorded.
 */
public class StudentAlreadyDeactivatedException extends RuntimeException {

    public StudentAlreadyDeactivatedException(Student student) {
        super(student.getFirstName() + " " + student.getLastName() + " is already deactivated.");
    }

}
