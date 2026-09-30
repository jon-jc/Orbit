package com.orbit.domain;

import static com.orbit.domain.DomainModels.*;

import com.orbit.auth.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workspaces")
public class DomainController {
    private final DomainService service;
    private final CurrentUser currentUser;

    public DomainController(DomainService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<Workspace> workspaces(Authentication auth) { return service.workspaces(currentUser.id(auth)); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Workspace createWorkspace(Authentication auth, @Valid @RequestBody CreateWorkspace input) { return service.createWorkspace(currentUser.id(auth), input); }

    @GetMapping("/{workspace}/members")
    public List<Member> members(Authentication auth, @PathVariable String workspace) { return service.members(workspace, currentUser.id(auth)); }

    @PostMapping("/{workspace}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public Member addMember(Authentication auth, @PathVariable String workspace, @Valid @RequestBody AddMember input) { return service.addMember(workspace, currentUser.id(auth), input); }

    @PatchMapping("/{workspace}/members/{user}")
    public Member updateMember(Authentication auth, @PathVariable String workspace, @PathVariable String user, @Valid @RequestBody UpdateMember input) { return service.updateMember(workspace, currentUser.id(auth), user, input); }

    @DeleteMapping("/{workspace}/members/{user}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(Authentication auth, @PathVariable String workspace, @PathVariable String user) { service.removeMember(workspace, currentUser.id(auth), user); }

    @GetMapping("/{workspace}/projects")
    public List<Project> projects(Authentication auth, @PathVariable String workspace) { return service.projects(workspace, currentUser.id(auth)); }

    @PostMapping("/{workspace}/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public Project createProject(Authentication auth, @PathVariable String workspace, @Valid @RequestBody CreateProject input) { return service.createProject(workspace, currentUser.id(auth), input); }

    @PatchMapping("/{workspace}/projects/{project}")
    public Project updateProject(Authentication auth, @PathVariable String workspace, @PathVariable String project, @Valid @RequestBody UpdateProject input) { return service.updateProject(workspace, currentUser.id(auth), project, input); }

    @GetMapping("/{workspace}/tasks")
    public Page<Task> tasks(Authentication auth, @PathVariable String workspace,
                            @RequestParam(required = false) String q,
                            @RequestParam(required = false) TaskStatus status,
                            @RequestParam(required = false) Priority priority,
                            @RequestParam(required = false) String projectId,
                            @RequestParam(required = false) String assigneeId,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "50") int size) {
        return service.tasks(workspace, currentUser.id(auth), q, status, priority, projectId, assigneeId, page, size);
    }

    @PostMapping("/{workspace}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public Task createTask(Authentication auth, @PathVariable String workspace, @Valid @RequestBody CreateTask input) { return service.createTask(workspace, currentUser.id(auth), input); }

    @GetMapping("/{workspace}/tasks/{task}")
    public Task task(Authentication auth, @PathVariable String workspace, @PathVariable String task) { return service.task(workspace, currentUser.id(auth), task); }

    @PatchMapping("/{workspace}/tasks/{task}")
    public Task updateTask(Authentication auth, @PathVariable String workspace, @PathVariable String task, @Valid @RequestBody UpdateTask input) { return service.updateTask(workspace, currentUser.id(auth), task, input); }

    @DeleteMapping("/{workspace}/tasks/{task}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTask(Authentication auth, @PathVariable String workspace, @PathVariable String task, @RequestParam long version) { service.deleteTask(workspace, currentUser.id(auth), task, version); }

    @GetMapping("/{workspace}/tasks/{task}/comments")
    public List<Comment> comments(Authentication auth, @PathVariable String workspace, @PathVariable String task) { return service.comments(workspace, currentUser.id(auth), task); }

    @PostMapping("/{workspace}/tasks/{task}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public Comment createComment(Authentication auth, @PathVariable String workspace, @PathVariable String task, @Valid @RequestBody CreateComment input) { return service.createComment(workspace, currentUser.id(auth), task, input); }

    @GetMapping("/{workspace}/activity")
    public Page<Activity> activity(Authentication auth, @PathVariable String workspace, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "30") int size) { return service.activity(workspace, currentUser.id(auth), page, size); }

    @GetMapping("/{workspace}/overview")
    public Overview overview(Authentication auth, @PathVariable String workspace) { return service.overview(workspace, currentUser.id(auth)); }

    @GetMapping("/{workspace}/export")
    public ResponseEntity<String> export(Authentication auth, @PathVariable String workspace) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"orbit-tasks.csv\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(service.export(workspace, currentUser.id(auth)));
    }
}
