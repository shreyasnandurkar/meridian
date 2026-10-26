package com.meridian.compare;

import com.meridian.common.CurrentUser;
import com.meridian.compare.CompareDtos.ComparisonRequest;
import com.meridian.compare.CompareDtos.ComparisonUpdate;
import com.meridian.compare.CompareDtos.ComparisonView;
import com.meridian.compare.CompareDtos.DraftView;
import com.meridian.compare.CompareDtos.RowRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/trips/{tripId}/comparisons")
public class CompareController {

    private final CompareService compare;

    public CompareController(CompareService compare) {
        this.compare = compare;
    }

    @GetMapping
    public List<ComparisonView> list(@PathVariable UUID tripId, @AuthenticationPrincipal Jwt jwt) {
        return compare.list(tripId, CurrentUser.id(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ComparisonView create(@PathVariable UUID tripId, @Valid @RequestBody ComparisonRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        return compare.create(tripId, CurrentUser.id(jwt), req);
    }

    @PatchMapping("/{comparisonId}")
    public ComparisonView update(@PathVariable UUID tripId, @PathVariable UUID comparisonId,
            @Valid @RequestBody ComparisonUpdate req, @AuthenticationPrincipal Jwt jwt) {
        return compare.update(tripId, comparisonId, CurrentUser.id(jwt), req);
    }

    @DeleteMapping("/{comparisonId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID tripId, @PathVariable UUID comparisonId, @AuthenticationPrincipal Jwt jwt) {
        compare.delete(tripId, comparisonId, CurrentUser.id(jwt));
    }

    @PostMapping("/{comparisonId}/rows")
    @ResponseStatus(HttpStatus.CREATED)
    public ComparisonView addRow(@PathVariable UUID tripId, @PathVariable UUID comparisonId,
            @Valid @RequestBody RowRequest req, @AuthenticationPrincipal Jwt jwt) {
        return compare.addRow(tripId, comparisonId, CurrentUser.id(jwt), req);
    }

    @PutMapping("/{comparisonId}/rows/{rowId}")
    public ComparisonView updateRow(@PathVariable UUID tripId, @PathVariable UUID comparisonId,
            @PathVariable UUID rowId, @Valid @RequestBody RowRequest req, @AuthenticationPrincipal Jwt jwt) {
        return compare.updateRow(tripId, comparisonId, rowId, CurrentUser.id(jwt), req);
    }

    @PostMapping("/{comparisonId}/rows/{rowId}/recheck")
    public ComparisonView recheck(@PathVariable UUID tripId, @PathVariable UUID comparisonId,
            @PathVariable UUID rowId, @AuthenticationPrincipal Jwt jwt) {
        return compare.recheck(tripId, comparisonId, rowId, CurrentUser.id(jwt));
    }

    @DeleteMapping("/{comparisonId}/rows/{rowId}")
    public ComparisonView deleteRow(@PathVariable UUID tripId, @PathVariable UUID comparisonId,
            @PathVariable UUID rowId, @AuthenticationPrincipal Jwt jwt) {
        return compare.deleteRow(tripId, comparisonId, rowId, CurrentUser.id(jwt));
    }

    /** Prefill a row from a pasted provider link. */
    @GetMapping("/draft")
    public DraftView draft(@PathVariable UUID tripId, @RequestParam @Size(max = 2048) String url,
            @AuthenticationPrincipal Jwt jwt) {
        return compare.draft(tripId, CurrentUser.id(jwt), url);
    }
}
