package com.orbit.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Local-only, editable demonstration. Existing data is never reset on restart. */
@Component
@Profile("local")
@ConditionalOnProperty(prefix = "orbit.demo", name = "enabled", havingValue = "true")
public class DemoData implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    public DemoData(JdbcTemplate jdbc, PasswordEncoder encoder) { this.jdbc = jdbc; this.encoder = encoder; }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        Long existing = jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE email='alex@orbit.local'", Long.class);
        if (existing != null && existing > 0) return;
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        LocalDate today = now.toLocalDate();
        String alex = user("Alex Morgan", "alex@orbit.local", now.minusDays(32));
        String maya = user("Maya Chen", "maya@orbit.local", now.minusDays(30));
        String jordan = user("Jordan Lee", "jordan@orbit.local", now.minusDays(30));
        String workspace = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO workspace(id,name,created_at) VALUES(?,?,?)", workspace, "Northstar Studio", now.minusDays(30));
        membership(workspace, alex, "OWNER", now.minusDays(30));
        membership(workspace, maya, "MEMBER", now.minusDays(29));
        membership(workspace, jordan, "MEMBER", now.minusDays(29));

        String launch = project(workspace, "Website relaunch", "A clearer home for Northstar: faster pages, thoughtful storytelling, and a launch we can be proud of.", "#7c6af2", now.minusDays(21));
        String platform = project(workspace, "Customer platform", "Make every customer journey feel effortless, from first sign-in to the next big milestone.", "#269d91", now.minusDays(18));
        String brand = project(workspace, "Brand & experience", "Bring a consistent, accessible visual language to every touchpoint.", "#e9a34d", now.minusDays(14));

        task(workspace, launch, "Map the new site architecture", "Confirm the top-level navigation and customer paths. Capture decisions in the shared project brief.", "DONE", "HIGH", alex, today.minusDays(9), now.minusDays(19), now.minusDays(10));
        task(workspace, launch, "Create homepage design explorations", "Explore an editorial hero, case-study cards, and a clear product story. Review desktop and mobile together.", "DONE", "HIGH", maya, today.minusDays(5), now.minusDays(17), now.minusDays(6));
        String hero = task(workspace, launch, "Build the responsive homepage", "Translate the approved direction into accessible components. Check navigation, images, and typography at every breakpoint.", "IN_PROGRESS", "HIGH", jordan, today.plusDays(2), now.minusDays(12), now.minusHours(2));
        task(workspace, launch, "Review the launch copy", "Polish headings, product benefits, and calls to action. Verify that all claims match what we ship.", "IN_REVIEW", "MEDIUM", alex, today.plusDays(1), now.minusDays(9), now.minusHours(4));
        task(workspace, launch, "Finish the accessibility audit", "Check keyboard navigation, focus states, color contrast, and screen-reader labels before launch.", "TODO", "URGENT", maya, today.minusDays(1), now.minusDays(6), now.minusHours(8));
        task(workspace, launch, "Set up page performance budgets", "Agree on image and script budgets and document the measurement process for the team.", "TODO", "MEDIUM", jordan, today.plusDays(4), now.minusDays(5), now.minusDays(1));
        task(workspace, launch, "Plan the launch announcement", "Draft the release note, coordinate customer communications, and define the launch-day checklist.", "BACKLOG", "LOW", alex, today.plusDays(7), now.minusDays(3), now.minusDays(2));

        task(workspace, platform, "Document the onboarding journey", "Identify the happy path and the moments where new customers need guidance. Share a lightweight journey map.", "DONE", "MEDIUM", maya, today.minusDays(8), now.minusDays(17), now.minusDays(9));
        task(workspace, platform, "Ship workspace invitations", "Implement clear role selection, invitation states, and helpful feedback for existing members.", "DONE", "HIGH", jordan, today.minusDays(4), now.minusDays(15), now.minusDays(5));
        String analytics = task(workspace, platform, "Design the customer health dashboard", "Show activation, usage, and milestones with useful context. Keep the first version focused on three metrics.", "IN_PROGRESS", "HIGH", maya, today.plusDays(3), now.minusDays(11), now.minusHours(1));
        task(workspace, platform, "Add audit history to account settings", "Make sensitive changes traceable and easy for administrators to review.", "IN_PROGRESS", "MEDIUM", jordan, today.plusDays(5), now.minusDays(10), now.minusHours(6));
        task(workspace, platform, "Validate the export workflow", "Check a complete export, Unicode data, spreadsheet-safe text, and the empty-state experience.", "IN_REVIEW", "HIGH", alex, today, now.minusDays(8), now.minusHours(3));
        task(workspace, platform, "Improve empty states", "Turn empty screens into clear next steps with useful examples and concise copy.", "TODO", "MEDIUM", maya, today.plusDays(6), now.minusDays(5), now.minusDays(1));
        task(workspace, platform, "Explore notification preferences", "Gather customer feedback and propose a focused set of controls that avoids notification fatigue.", "BACKLOG", "LOW", null, null, now.minusDays(2), now.minusDays(2));

        task(workspace, brand, "Define our design principles", "Write five principles that help the team make consistent product decisions. Include concrete examples.", "DONE", "MEDIUM", alex, today.minusDays(7), now.minusDays(13), now.minusDays(8));
        task(workspace, brand, "Publish the color and type foundations", "Document accessible pairings, semantic colors, heading sizes, and responsive typography.", "DONE", "HIGH", maya, today.minusDays(3), now.minusDays(11), now.minusDays(4));
        String library = task(workspace, brand, "Refine the component library", "Complete buttons, form inputs, dialogs, and task cards with all interaction and error states.", "IN_PROGRESS", "HIGH", maya, today.plusDays(2), now.minusDays(9), now.minusMinutes(35));
        task(workspace, brand, "Review mobile navigation patterns", "Test workspace switching and primary navigation on a narrow screen. Confirm all controls remain reachable.", "IN_REVIEW", "MEDIUM", jordan, today.plusDays(1), now.minusDays(7), now.minusHours(5));
        task(workspace, brand, "Create a product illustration style", "Explore a small, coherent visual vocabulary for onboarding and project milestones.", "TODO", "LOW", maya, today.plusDays(8), now.minusDays(4), now.minusDays(1));
        task(workspace, brand, "Audit product microcopy", "Review buttons, error messages, and confirmations for clarity and consistent language.", "TODO", "MEDIUM", alex, today.minusDays(2), now.minusDays(4), now.minusHours(7));
        task(workspace, brand, "Collect customer feedback on the refresh", "Prepare a short research script and recruit five customers for a first-impression session.", "BACKLOG", "LOW", null, today.plusDays(12), now.minusDays(2), now.minusDays(2));

        comment(workspace, hero, maya, "The mobile layout is ready for review. I added a tighter spacing scale for small screens.", now.minusHours(8));
        comment(workspace, hero, jordan, "Great, I'll bring that into the implementation today and check the keyboard navigation too.", now.minusHours(2));
        comment(workspace, analytics, alex, "Let's keep activation and first value visible above the fold. The rest can come in the next iteration.", now.minusHours(5));
        comment(workspace, analytics, maya, "Updated the first pass with three focused metrics and a clearer date range.", now.minusHours(1));
        comment(workspace, library, jordan, "The dialog focus handling looks good. Could we add a destructive confirmation example?", now.minusHours(4));
        comment(workspace, library, maya, "Added a delete-task confirmation and all the loading states. Ready for another look.", now.minusMinutes(35));

        activity(workspace, alex, "created", "workspace", "Northstar Studio", now.minusDays(30));
        activity(workspace, alex, "created", "project", "Website relaunch", now.minusDays(21));
        activity(workspace, alex, "created", "project", "Customer platform", now.minusDays(18));
        activity(workspace, alex, "created", "project", "Brand & experience", now.minusDays(14));
        activity(workspace, jordan, "completed", "task", "Ship workspace invitations", now.minusDays(5));
        activity(workspace, maya, "completed", "task", "Publish the color and type foundations", now.minusDays(4));
        activity(workspace, jordan, "moved to in review", "task", "Review mobile navigation patterns", now.minusHours(5));
        activity(workspace, alex, "moved to in review", "task", "Validate the export workflow", now.minusHours(3));
        activity(workspace, jordan, "commented on", "task", "Build the responsive homepage", now.minusHours(2));
        activity(workspace, maya, "updated", "task", "Design the customer health dashboard", now.minusHours(1));
        activity(workspace, maya, "commented on", "task", "Refine the component library", now.minusMinutes(35));
    }

    private String user(String name, String email, OffsetDateTime created) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO app_user(id,email,display_name,password_hash,created_at,email_verified) VALUES(?,?,?,?,?,TRUE)", id, email, name, encoder.encode("OrbitDemo!2026"), created);
        return id;
    }

    private void membership(String workspace, String user, String role, OffsetDateTime created) {
        jdbc.update("INSERT INTO workspace_member(workspace_id,user_id,role,created_at) VALUES(?,?,?,?)", workspace, user, role, created);
    }

    private String project(String workspace, String name, String description, String color, OffsetDateTime created) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO project(id,workspace_id,name,description,color,status,version,created_at,updated_at) VALUES(?,?,?,?,?,'ACTIVE',0,?,?)", id, workspace, name, description, color, created, created);
        return id;
    }

    private String task(String workspace, String project, String title, String description, String status,
                         String priority, String assignee, LocalDate due, OffsetDateTime created, OffsetDateTime updated) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO task(id,workspace_id,project_id,title,description,status,priority,assignee_id,due_date,version,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,0,?,?)",
                id, workspace, project, title, description, status, priority, assignee, due, created, updated);
        return id;
    }

    private void comment(String workspace, String task, String author, String body, OffsetDateTime created) {
        jdbc.update("INSERT INTO task_comment(id,workspace_id,task_id,author_id,body,created_at) VALUES(?,?,?,?,?,?)", UUID.randomUUID().toString(), workspace, task, author, body, created);
    }

    private void activity(String workspace, String actor, String action, String type, String name, OffsetDateTime created) {
        jdbc.update("INSERT INTO activity_event(id,workspace_id,actor_id,action,entity_type,entity_name,created_at) VALUES(?,?,?,?,?,?,?)", UUID.randomUUID().toString(), workspace, actor, action, type, name, created);
    }
}
