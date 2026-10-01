package com.orbit.domain;

import static com.orbit.domain.ProductivityModels.*;

import com.orbit.auth.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workspaces/{workspace}")
public class ProductivityController {
    private final DomainService tasks;
    private final SavedViewService views;
    private final CurrentUser current;

    public ProductivityController(DomainService tasks, SavedViewService views, CurrentUser current) {
        this.tasks = tasks; this.views = views; this.current = current;
    }

    @PostMapping("/tasks/bulk")
    public List<DomainModels.Task> bulk(Authentication auth, @PathVariable String workspace,
            @Valid @RequestBody BulkTasks input) {
        return tasks.bulkTasks(workspace, current.id(auth), input);
    }

    @GetMapping("/saved-views")
    public List<SavedView> views(Authentication auth, @PathVariable String workspace) {
        return views.list(workspace, current.id(auth));
    }

    @PostMapping("/saved-views") @ResponseStatus(HttpStatus.CREATED)
    public SavedView create(Authentication auth, @PathVariable String workspace, @Valid @RequestBody CreateView input) {
        return views.create(workspace, current.id(auth), input);
    }

    @GetMapping("/saved-views/{view}")
    public SavedView view(Authentication auth, @PathVariable String workspace, @PathVariable String view) {
        return views.get(workspace, current.id(auth), view);
    }

    @PatchMapping("/saved-views/{view}")
    public SavedView update(Authentication auth, @PathVariable String workspace, @PathVariable String view,
            @Valid @RequestBody UpdateView input) {
        return views.update(workspace, current.id(auth), view, input);
    }

    @DeleteMapping("/saved-views/{view}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable String workspace, @PathVariable String view) {
        views.delete(workspace, current.id(auth), view);
    }
}
