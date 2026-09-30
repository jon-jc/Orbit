package com.orbit.domain;

import com.orbit.auth.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import static com.orbit.domain.CollaborationModels.*;

@RestController
@RequestMapping("/api")
public class CollaborationController {
    private final CollaborationService collaboration;
    private final NotificationService notifications;
    private final CurrentUser current;
    public CollaborationController(CollaborationService collaboration, NotificationService notifications, CurrentUser current) {
        this.collaboration = collaboration; this.notifications = notifications; this.current = current;
    }
    @GetMapping("/workspaces/{workspace}/settings")
    public Settings settings(Authentication auth, @PathVariable String workspace) { return collaboration.settings(workspace, current.id(auth)); }
    @PatchMapping("/workspaces/{workspace}/settings")
    public Settings rename(Authentication auth, @PathVariable String workspace, @Valid @RequestBody Rename input) { return collaboration.rename(workspace, current.id(auth), input); }
    @GetMapping("/workspaces/{workspace}/invitations")
    public List<Invitation> invitations(Authentication auth, @PathVariable String workspace) { return collaboration.invitations(workspace, current.id(auth)); }
    @PostMapping("/workspaces/{workspace}/invitations") @ResponseStatus(HttpStatus.CREATED)
    public Invitation invite(Authentication auth, @PathVariable String workspace, @Valid @RequestBody Invite input) { return collaboration.invite(workspace, current.id(auth), input); }
    @DeleteMapping("/workspaces/{workspace}/invitations/{invitation}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(Authentication auth, @PathVariable String workspace, @PathVariable String invitation) { collaboration.revoke(workspace, current.id(auth), invitation); }
    @GetMapping("/invitations/preview")
    public Preview preview(@RequestParam String token) { return collaboration.preview(token); }
    @PostMapping("/invitations/accept")
    public DomainModels.Workspace accept(Authentication auth, @Valid @RequestBody Accept input) { return collaboration.accept(current.id(auth), input.token()); }
    @GetMapping("/notifications")
    public Inbox inbox(Authentication auth, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "30") int size) { return notifications.inbox(current.id(auth), page, size); }
    @PatchMapping("/notifications/{notification}/read") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(Authentication auth, @PathVariable String notification) { notifications.read(current.id(auth), notification); }
    @PostMapping("/notifications/read-all") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(Authentication auth) { notifications.readAll(current.id(auth)); }
}
