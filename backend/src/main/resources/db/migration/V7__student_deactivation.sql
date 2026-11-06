-- UC-STU-deactivate-student: record which team a deactivated student was removed from, and in which week.
-- Both columns are null for a student who was never deactivated (and former_team_id also for one deactivated
-- while on no team). ON DELETE SET NULL so that deleting a team is not blocked by its former members; the
-- Student entity declares the same rule with @OnDelete, so dev's ddl-auto schema matches this one.

ALTER TABLE `peer_evaluation_user`
  ADD COLUMN `former_team_id` int DEFAULT NULL,
  ADD COLUMN `deactivated_week` varchar(255) DEFAULT NULL,
  ADD CONSTRAINT `fk_peer_evaluation_user_former_team`
    FOREIGN KEY (`former_team_id`) REFERENCES `team` (`team_id`) ON DELETE SET NULL;
