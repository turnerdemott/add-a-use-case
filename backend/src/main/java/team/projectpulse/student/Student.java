package team.projectpulse.student;

import team.projectpulse.section.Section;
import team.projectpulse.team.Team;
import team.projectpulse.user.PeerEvaluationUser;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
public class Student extends PeerEvaluationUser {

    @ManyToOne(optional = false)
    private Section section;
    @ManyToOne
    private Team team;

    /**
     * The team she was removed from when she was deactivated (BR-student-lifecycle), or null if she was on no team
     * then or was never deactivated. Her former teammates may evaluate her, and the section report shows her under
     * this team, for every week up to and including {@link #deactivatedWeek}.
     *
     * <p>{@code ON DELETE SET NULL} so that deleting the team is not blocked by its former members; declared here
     * as well as in {@code V7__student_deactivation.sql} so that {@code ddl-auto: create} builds the same key.
     */
    @ManyToOne
    @JoinColumn(name = "former_team_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Team formerTeam;

    /**
     * The week key ({@code YYYY-Www}) of the week she was deactivated in, or null if she never was. Week keys sort
     * lexically, so it is compared with other week keys as a string.
     */
    private String deactivatedWeek;


    public Student() {
    }

    public Student(String username, String firstName, String lastName, String email, String password, boolean enabled, String roles) {
        super(username, firstName, lastName, email, password, enabled, roles);
    }

    public Section getSection() {
        return section;
    }

    public void setSection(Section section) {
        this.section = section;
    }

    public Team getTeam() {
        return team;
    }

    public void setTeam(Team team) {
        this.team = team;
    }

    public Team getFormerTeam() {
        return formerTeam;
    }

    public void setFormerTeam(Team formerTeam) {
        this.formerTeam = formerTeam;
    }

    /**
     * Whether, for the given week, she still counts as a member of the team she was removed from when she was
     * deactivated: her former teammates may evaluate her for that week, and the section report lists her
     * (BR-student-lifecycle). True for the week of her deactivation and every earlier week.
     */
    public boolean isFormerMemberInWeek(String week) {
        return this.formerTeam != null && isOnOrBeforeDeactivation(week);
    }

    /**
     * Whether she was deactivated and the given week is the week of her deactivation or an earlier one. Week keys sort
     * lexically, so they compare as strings. The one statement of that boundary (BR-student-lifecycle).
     */
    public boolean isOnOrBeforeDeactivation(String week) {
        return this.deactivatedWeek != null && week.compareTo(this.deactivatedWeek) <= 0;
    }

    /**
     * The team she is reported under for the given week: the team she was removed from, for the week of her
     * deactivation and earlier weeks, and her current team (possibly none) otherwise. A student later reactivated
     * and reassigned therefore still shows under her first team for the weeks before.
     */
    public Team teamInWeek(String week) {
        if (isOnOrBeforeDeactivation(week)) {
            return this.formerTeam;
        }
        return this.team;
    }

    public String getDeactivatedWeek() {
        return deactivatedWeek;
    }

    public void setDeactivatedWeek(String deactivatedWeek) {
        this.deactivatedWeek = deactivatedWeek;
    }

}
